package com.schwab.orchestrator.execution;

import com.schwab.orchestrator.agents.Agent;
import com.schwab.orchestrator.agents.AgentRegistry;
import com.schwab.orchestrator.agents.FallbackAgentEngine;
import com.schwab.orchestrator.agents.StageAgentResult;
import com.schwab.orchestrator.audit.AuditLogger;
import com.schwab.orchestrator.governance.GovernanceEngine;
import com.schwab.orchestrator.governance.GuardrailResult;
import com.schwab.orchestrator.graph.DependencyGraph;
import com.schwab.orchestrator.graph.GateType;
import com.schwab.orchestrator.graph.GraphBuilder;
import com.schwab.orchestrator.graph.StageDefinition;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.graph.StageStatus;
import com.schwab.orchestrator.model.ApprovalRequest;
import com.schwab.orchestrator.model.CheckpointKind;
import com.schwab.orchestrator.model.DecisionRecord;
import com.schwab.orchestrator.model.RunRequest;
import com.schwab.orchestrator.model.RunState;
import com.schwab.orchestrator.model.StageOutput;
import com.schwab.orchestrator.reliability.MetricsRegistry;
import com.schwab.orchestrator.reliability.ReplanningEngine;
import com.schwab.orchestrator.reliability.RetryPolicy;
import com.schwab.orchestrator.reliability.RollbackRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The orchestration core: a small, event-driven state machine (see {@link #tick}) that walks the
 * run's {@link DependencyGraph}, scheduling every stage whose dependencies are satisfied, honoring
 * entry/exit human-approval gates, evaluating policy guardrails, retrying failed stage attempts
 * with bounded backoff, falling back to a deterministic agent when the LLM is unavailable, rolling
 * back completed work when a dependent stage fails permanently, and re-planning the graph itself
 * when an upstream stage's output demands it (see {@link ReplanningEngine}).
 *
 * Concurrency model: each {@link Run} has its own monitor ({@code run.stateLock()}). Structural
 * state changes (status map, graph mutation, checkpoint resolution, run state) happen only while
 * holding that lock, and only ever briefly — the lock is never held across an agent call, a sleep,
 * or I/O. Independent stages within one topological batch (e.g. TESTING and DOCUMENTATION) are
 * submitted to a shared virtual-thread executor and genuinely run concurrently; {@link #tick} is
 * what synchronizes them back together at the RELEASE_READINESS join point.
 */
public final class ExecutionEngine {
    private final AgentRegistry agentRegistry;
    private final FallbackAgentEngine fallbackAgentEngine;
    private final GovernanceEngine governanceEngine;
    private final ReplanningEngine replanningEngine;
    private final RollbackRegistry rollbackRegistry;
    private final MetricsRegistry metrics;
    private final AuditLogger auditLogger;
    private final GraphBuilder graphBuilder = new GraphBuilder();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutionEngine(AgentRegistry agentRegistry, FallbackAgentEngine fallbackAgentEngine,
                            GovernanceEngine governanceEngine, ReplanningEngine replanningEngine,
                            RollbackRegistry rollbackRegistry, MetricsRegistry metrics, AuditLogger auditLogger) {
        this.agentRegistry = agentRegistry;
        this.fallbackAgentEngine = fallbackAgentEngine;
        this.governanceEngine = governanceEngine;
        this.replanningEngine = replanningEngine;
        this.rollbackRegistry = rollbackRegistry;
        this.metrics = metrics;
        this.auditLogger = auditLogger;
    }

    public Run startRun(RunRequest request) {
        DependencyGraph graph = graphBuilder.build(request.scenarioType());
        Run run = new Run(request.runId(), request, graph);
        metrics.runStarted();
        audit(run, null, "run_started", "SYSTEM",
                "Run started: scenario=" + request.scenarioType() + " title=" + request.title(), Map.of());
        synchronized (run.stateLock()) {
            run.setState(RunState.RUNNING);
            tick(run);
        }
        return run;
    }

    public void requestSafeStop(Run run, String reason) {
        synchronized (run.stateLock()) {
            if (run.isTerminal()) return;
            run.requestSafeStop(reason);
            audit(run, null, "safe_stop_requested", "HUMAN", "Safe stop requested: " + reason, Map.of());
            tick(run);
        }
    }

    /** Resolves a pending approval checkpoint (entry or exit gate) and resumes scheduling. */
    public void decideApproval(Run run, String checkpointId, ApprovalRequest.Decision decision, String approver, String comment) {
        synchronized (run.stateLock()) {
            if (run.isTerminal()) {
                throw new IllegalStateException("Run " + run.id() + " is already terminal (" + run.state() + ")");
            }
            if (decision == null || decision == ApprovalRequest.Decision.PENDING) {
                throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
            }
            if (approver == null || approver.isBlank()) {
                throw new IllegalArgumentException("Approver is required");
            }
            ApprovalRequest cp = run.checkpoint(checkpointId);
            if (cp == null) {
                throw new IllegalArgumentException("No such checkpoint: " + checkpointId);
            }
            if (cp.stage() == StageId.CLARIFICATION
                    && cp.kind() == CheckpointKind.ENTRY
                    && decision == ApprovalRequest.Decision.APPROVED
                    && (comment == null || comment.isBlank())) {
                throw new IllegalArgumentException(
                        "A clarification answer is required in comment");
            }
            boolean applied = cp.resolve(decision, approver, comment);
            if (!applied) {
                throw new IllegalStateException("Checkpoint " + checkpointId + " was already decided (" + cp.decision() + ")");
            }
            if (cp.stage() == StageId.CLARIFICATION && cp.kind() == CheckpointKind.ENTRY && decision == ApprovalRequest.Decision.APPROVED) {
                run.context().setClarificationAnswer(comment);
            }
            if (decision == ApprovalRequest.Decision.APPROVED) {
                metrics.approvalGranted();
                run.context().decide(new DecisionRecord(Instant.now(), cp.stage(), "HUMAN",
                        "Approved " + cp.kind() + " checkpoint for " + cp.stage(), comment == null ? "(no comment)" : comment));
                audit(run, cp.stage(), "approval_granted", approver, "Approved " + checkpointId + (comment != null ? ": " + comment : ""), Map.of());
                if (cp.kind() == CheckpointKind.ENTRY && run.statusOf(cp.stage()) == StageStatus.WAITING_APPROVAL) {
                    run.setStatus(cp.stage(), StageStatus.PENDING); // becomes a schedulable candidate again on this tick
                }
                tick(run);
            } else {
                metrics.approvalRejected();
                audit(run, cp.stage(), "approval_rejected", approver, "Rejected " + checkpointId + (comment != null ? ": " + comment : ""), Map.of());
                if (cp.kind() == CheckpointKind.ENTRY) {
                    run.setStatus(cp.stage(), StageStatus.FAILED);
                }
                finalizeRunLocked(run, RunState.FAILED, "Human rejected " + cp.kind() + " approval for " + cp.stage());
                metrics.runFailed(Duration.between(run.createdAt(), Instant.now()));
            }
        }
    }

    // ------------------------------------------------------------------- core loop

    private void tick(Run run) {
        // Caller already holds run.stateLock(); synchronized is reentrant so this is safe to call
        // both as the entry point and recursively from stage-completion callbacks on the same thread.
        synchronized (run.stateLock()) {
            if (run.isTerminal()) return;
            if (run.isSafeStopRequested()) {
                finalizeRunLocked(run, RunState.SAFE_STOPPED, run.terminalReason() == null ? "Safe stop requested" : run.terminalReason());
                return;
            }
            if (run.state() == RunState.BLOCKED_GUARDRAIL) return; // requires external intervention; not auto-resumed

            boolean anyRunning = false;
            boolean anyWaitingApproval = false;
            boolean allDone = true;

            for (StageId id : run.stageIds()) {
                StageStatus status = run.statusOf(id);
                StageDefinition def = run.graph().get(id);

                if (status == StageStatus.RUNNING) {
                    anyRunning = true;
                    allDone = false;
                    continue;
                }
                if (status == StageStatus.SUCCEEDED || status == StageStatus.SKIPPED) {
                    if (def.exitGate() == GateType.HUMAN_APPROVAL) {
                        String cpId = checkpointId(id, CheckpointKind.EXIT);
                        ApprovalRequest cp = run.checkpoint(cpId);
                        if (cp == null) {
                            run.openCheckpoint(new ApprovalRequest(cpId, id, CheckpointKind.EXIT,
                                    "Exit approval required before " + id + " output can be relied on", Instant.now()));
                            audit(run, id, "approval_requested", "SYSTEM", "Exit approval requested for " + id, Map.of());
                            anyWaitingApproval = true;
                            allDone = false;
                        } else if (cp.isPending()) {
                            anyWaitingApproval = true;
                            allDone = false;
                        }
                    }
                    continue;
                }
                if (status == StageStatus.FAILED || status == StageStatus.BLOCKED_GUARDRAIL || status == StageStatus.ROLLED_BACK) {
                    allDone = false; // defensive; run should already be terminal via finalizeRunLocked when these occur
                    continue;
                }
                if (status == StageStatus.WAITING_APPROVAL) {
                    anyWaitingApproval = true;
                    allDone = false;
                    continue;
                }

                // status == PENDING
                allDone = false;
                boolean depsSatisfied = run.graph().dependenciesOf(id).stream()
                        .allMatch(d -> run.statusOf(d) == StageStatus.SUCCEEDED || run.statusOf(d) == StageStatus.SKIPPED);
                if (!depsSatisfied) continue;

                if (def.entryGate() == GateType.HUMAN_APPROVAL) {
                    String cpId = checkpointId(id, CheckpointKind.ENTRY);
                    ApprovalRequest cp = run.checkpoint(cpId);
                    if (cp == null) {
                        run.openCheckpoint(new ApprovalRequest(cpId, id, CheckpointKind.ENTRY,
                                "Entry approval required before " + id + " (high-impact action) may run", Instant.now()));
                        run.setStatus(id, StageStatus.WAITING_APPROVAL);
                        audit(run, id, "approval_requested", "SYSTEM", "Entry approval requested for " + id, Map.of());
                        anyWaitingApproval = true;
                        continue;
                    } else if (cp.isPending()) {
                        run.setStatus(id, StageStatus.WAITING_APPROVAL);
                        anyWaitingApproval = true;
                        continue;
                    } else if (cp.decision() == ApprovalRequest.Decision.REJECTED) {
                        continue; // run already finalized as FAILED by decideApproval()
                    }
                    // APPROVED: fall through to guardrails + scheduling below
                }

                List<GuardrailResult> results = governanceEngine.evaluateAll(id, run.context(), run.request());
                for (GuardrailResult r : results) {
                    audit(run, id, "guardrail_evaluated", "SYSTEM",
                            r.guardrailName() + ": " + (r.allowed() ? "pass" : "BLOCK - " + r.reason()), Map.of());
                }
                if (governanceEngine.anyBlocking(results)) {
                    run.setStatus(id, StageStatus.BLOCKED_GUARDRAIL);
                    metrics.guardrailBlocked();
                    finalizeRunLocked(run, RunState.BLOCKED_GUARDRAIL, "Guardrail blocked stage " + id);
                    metrics.runFailed(Duration.between(run.createdAt(), Instant.now()));
                    return;
                }

                run.setStatus(id, StageStatus.RUNNING);
                anyRunning = true;
                executor.submit(() -> executeStageWithRetry(run, id));
            }

            if (allDone) {
                finalizeRunLocked(run, RunState.COMPLETED, "All stages succeeded and all approval gates cleared");
                metrics.runSucceeded(Duration.between(run.createdAt(), Instant.now()));
                return;
            }
            run.setState(anyWaitingApproval && !anyRunning ? RunState.WAITING_APPROVAL : RunState.RUNNING);
        }
    }

    // ------------------------------------------------------------------- stage execution

    private void executeStageWithRetry(Run run, StageId stageId) {
        StageDefinition def = run.graph().get(stageId);
        RetryPolicy retryPolicy = new RetryPolicy(def.maxAttempts(), 150, 2.0);
        Agent agent = agentRegistry.agentFor(stageId);

        StageAgentResult result = null;
        Exception lastError = null;
        boolean usedFallback = false;
        Instant startedAt = Instant.now();

        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            run.nextAttempt(stageId);
            audit(run, stageId, "stage_attempt_started", "AGENT",
                    "Attempt " + attempt + "/" + retryPolicy.maxAttempts(), Map.of("attempt", attempt));
            metrics.stageExecuted();
            try {
                result = agent.execute(stageId, run.context(), run.request());
                break;
            } catch (Exception e) {
                lastError = e;
                audit(run, stageId, "stage_attempt_failed", "AGENT",
                        "Attempt " + attempt + " failed: " + safeMessage(e), Map.of("attempt", attempt));
                if (attempt < retryPolicy.maxAttempts()) {
                    metrics.stageRetried();
                    sleep(retryPolicy.delayBeforeAttempt(attempt));
                }
            }
        }

        if (result == null) {
            try {
                result = fallbackAgentEngine.generate(stageId, run.context(), run.request());
                usedFallback = true;
                metrics.fallbackUsed();
                audit(run, stageId, "fallback_used", "SYSTEM",
                        "Exhausted " + retryPolicy.maxAttempts() + " attempts (" + safeMessage(lastError) + "); used deterministic fallback", Map.of());
            } catch (Exception fallbackError) {
                lastError = fallbackError;
            }
        }

        Instant recoveredAt = Instant.now();
        if (lastError != null && result != null) {
            metrics.recordRecovery(startedAt, recoveredAt); // failed at least once, then recovered (via retry or fallback)
        }

        synchronized (run.stateLock()) {
            if (run.isTerminal()) return; // e.g. safe-stopped while this stage was in flight

            if (result == null) {
                run.setStatus(stageId, StageStatus.FAILED);
                String msg = "Stage " + stageId + " failed permanently after " + retryPolicy.maxAttempts() + " attempts: " + safeMessage(lastError);
                audit(run, stageId, "stage_failed_permanently", "SYSTEM", msg, Map.of());
                boolean rolledBack = performRollback(run, def);
                finalizeRunLocked(run, rolledBack ? RunState.ROLLED_BACK : RunState.FAILED, msg);
                metrics.runFailed(Duration.between(run.createdAt(), recoveredAt));
                return;
            }

            StageOutput output = new StageOutput(stageId, run.attemptCount(stageId), startedAt);
            output.setStatus(StageStatus.SUCCEEDED);
            output.setFinishedAt(recoveredAt);
            output.setSummary(result.summary());
            output.setArtifacts(result.artifacts());
            output.setRiskFlags(result.riskFlags());
            output.setRequiresHumanReview(result.requiresHumanReview());
            output.setAmbiguous(result.ambiguous());
            output.setClarifyingQuestions(result.clarifyingQuestions());
            output.setUsedFallback(usedFallback);
            run.context().recordOutput(output);
            run.setStatus(stageId, StageStatus.SUCCEEDED);

            for (StageAgentResult.Decision d : result.decisions()) {
                run.context().decide(new DecisionRecord(Instant.now(), stageId, "AGENT", d.description(), d.rationale()));
            }
            audit(run, stageId, "stage_succeeded", "AGENT",
                    "Succeeded on attempt " + output.attempt() + (usedFallback ? " (fallback)" : ""), Map.of());

            if (replanningEngine.evaluateAfter(run, stageId, output)) {
                ReplanEvent latest = run.replans().get(run.replans().size() - 1);
                audit(run, stageId, "replanned", "SYSTEM", latest.reason() + " -> " + latest.graphChange(), Map.of());
            }

            tick(run);
        }
    }

    private boolean performRollback(Run run, StageDefinition failedStageDef) {
        boolean any = false;
        for (StageId target : failedStageDef.rollbackTargets()) {
            if (run.statusOf(target) == StageStatus.SUCCEEDED && rollbackRegistry.supports(target)) {
                try {
                    String description = rollbackRegistry.get(target).rollback(target, run.context());
                    run.setStatus(target, StageStatus.ROLLED_BACK);
                    metrics.rollbackPerformed();
                    audit(run, target, "rollback_performed", "SYSTEM", description, Map.of());
                    any = true;
                } catch (RuntimeException rollbackError) {
                    audit(run, target, "rollback_failed", "SYSTEM",
                            "Rollback failed: " + safeMessage(rollbackError), Map.of());
                }
            }
        }
        return any;
    }

    private void finalizeRunLocked(Run run, RunState state, String reason) {
        run.setState(state);
        run.setTerminalReason(reason);
        run.markFinished();
        audit(run, null, "run_finalized", "SYSTEM", "Run finalized as " + state + ": " + reason, Map.of());
    }

    private String checkpointId(StageId stage, CheckpointKind kind) {
        return stage.name() + ":" + kind.name();
    }

    private void audit(Run run, StageId stage, String type, String actor, String message, Map<String, Object> data) {
        auditLogger.log(run.id(), stage, type, actor, message, data);
    }

    private String safeMessage(Exception e) {
        return e == null ? "unknown error" : String.valueOf(e.getMessage());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

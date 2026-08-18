package com.schwab.orchestrator.execution;

import com.schwab.orchestrator.graph.DependencyGraph;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.graph.StageStatus;
import com.schwab.orchestrator.model.ApprovalRequest;
import com.schwab.orchestrator.model.RunRequest;
import com.schwab.orchestrator.model.RunState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * All mutable state for one orchestration run. A single {@code stateLock} guards structural
 * transitions (status map, graph mutation, run state); the lock is only ever held for short,
 * in-memory bookkeeping — never across an agent call or a sleep — so retries/backoff on one stage
 * don't block progress on independent parallel stages.
 */
public final class Run {
    private final String id;
    private final RunRequest request;
    private volatile DependencyGraph graph;
    private final ExecutionContext context;
    private final Object stateLock = new Object();

    private final Map<StageId, StageStatus> stageStatus = new LinkedHashMap<>();
    private final Map<StageId, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();
    private final Map<String, ApprovalRequest> checkpoints = new LinkedHashMap<>();
    private final List<ReplanEvent> replans = new CopyOnWriteArrayList<>();
    private volatile RunState state = RunState.PENDING;
    private final Instant createdAt = Instant.now();
    private volatile Instant finishedAt;
    private final AtomicBoolean safeStopRequested = new AtomicBoolean(false);
    private volatile String terminalReason;

    public Run(String id, RunRequest request, DependencyGraph graph) {
        this.id = id;
        this.request = request;
        this.graph = graph;
        this.context = new ExecutionContext(request);
        for (StageId sid : graph.allStageIds()) {
            stageStatus.put(sid, StageStatus.PENDING);
        }
    }

    public String id() {
        return id;
    }

    public RunRequest request() {
        return request;
    }

    public ExecutionContext context() {
        return context;
    }

    public Object stateLock() {
        return stateLock;
    }

    public DependencyGraph graph() {
        return graph;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public void markFinished() {
        this.finishedAt = Instant.now();
    }

    public String terminalReason() {
        return terminalReason;
    }

    public void setTerminalReason(String reason) {
        this.terminalReason = reason;
    }

    // -------------------------------------------------------------- graph mutation (re-plan)

    /** Adds a stage and rewires the given stage's dependency set to point at it too. Must be called under stateLock. */
    public void spliceStage(com.schwab.orchestrator.graph.StageDefinition newStage, StageId rewireDependent,
                             com.schwab.orchestrator.graph.StageDefinition rewiredDependentDef) {
        graph.addStage(newStage);
        stageStatus.putIfAbsent(newStage.id(), StageStatus.PENDING);
        graph.replaceStage(rewiredDependentDef);
    }

    public void recordReplan(ReplanEvent event) {
        replans.add(event);
    }

    public List<ReplanEvent> replans() {
        return List.copyOf(replans);
    }

    // -------------------------------------------------------------- stage status

    public StageStatus statusOf(StageId id) {
        return stageStatus.get(id);
    }

    public void setStatus(StageId id, StageStatus status) {
        stageStatus.put(id, status);
    }

    public Map<StageId, StageStatus> allStatuses() {
        return Map.copyOf(stageStatus);
    }

    public int nextAttempt(StageId id) {
        return attemptCounts.computeIfAbsent(id, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public int attemptCount(StageId id) {
        AtomicInteger c = attemptCounts.get(id);
        return c == null ? 0 : c.get();
    }

    // -------------------------------------------------------------- approvals / checkpoints

    public ApprovalRequest openCheckpoint(ApprovalRequest req) {
        checkpoints.putIfAbsent(req.checkpointId(), req);
        return checkpoints.get(req.checkpointId());
    }

    public ApprovalRequest checkpoint(String checkpointId) {
        return checkpoints.get(checkpointId);
    }

    public List<ApprovalRequest> pendingCheckpoints() {
        return checkpoints.values().stream().filter(ApprovalRequest::isPending).toList();
    }

    public List<ApprovalRequest> allCheckpoints() {
        return List.copyOf(checkpoints.values());
    }

    // -------------------------------------------------------------- run state

    public RunState state() {
        return state;
    }

    public void setState(RunState state) {
        this.state = state;
    }

    public boolean isTerminal() {
        // BLOCKED_GUARDRAIL is treated as terminal in this prototype: there is no implemented
        // override/resume path for a policy block (that would be a real extension point — see
        // TESTING_AND_TRADEOFFS.md), so once governance blocks a stage the run does not progress
        // further on its own.
        return state == RunState.COMPLETED || state == RunState.FAILED
                || state == RunState.ROLLED_BACK || state == RunState.SAFE_STOPPED
                || state == RunState.BLOCKED_GUARDRAIL;
    }

    public void requestSafeStop(String reason) {
        safeStopRequested.set(true);
        setTerminalReason(reason);
    }

    public boolean isSafeStopRequested() {
        return safeStopRequested.get();
    }

    public Set<StageId> stageIds() {
        return graph.allStageIds();
    }
}

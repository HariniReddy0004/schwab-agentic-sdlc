package com.schwab.orchestrator.testing;

import com.schwab.orchestrator.agents.AgentRegistry;
import com.schwab.orchestrator.agents.FallbackAgentEngine;
import com.schwab.orchestrator.audit.AuditLogger;
import com.schwab.orchestrator.execution.ExecutionEngine;
import com.schwab.orchestrator.execution.Run;
import com.schwab.orchestrator.governance.ChangeControlGuardrail;
import com.schwab.orchestrator.governance.ComplianceGuardrail;
import com.schwab.orchestrator.governance.GovernanceEngine;
import com.schwab.orchestrator.governance.SecurityGuardrail;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.llm.ClaudeClient;
import com.schwab.orchestrator.model.RunRequest;
import com.schwab.orchestrator.model.RunState;
import com.schwab.orchestrator.reliability.MetricsRegistry;
import com.schwab.orchestrator.reliability.ReplanningEngine;
import com.schwab.orchestrator.reliability.RollbackRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Builds a fully-wired ExecutionEngine against a scratch audit-log directory, and small polling helpers for async assertions. */
public final class EngineTestHarness {
    public final ExecutionEngine engine;
    public final AuditLogger auditLogger;
    public final MetricsRegistry metrics;
    public final RollbackRegistry rollbackRegistry;

    public EngineTestHarness() {
        this.auditLogger = new AuditLogger(tempDir());
        this.metrics = new MetricsRegistry();
        this.rollbackRegistry = new RollbackRegistry();
        rollbackRegistry.register(StageId.IMPLEMENTATION, (stageId, ctx) -> "Rolled back IMPLEMENTATION (test harness)");
        GovernanceEngine governanceEngine = new GovernanceEngine(List.of(
                new SecurityGuardrail(), new ComplianceGuardrail(), new ChangeControlGuardrail()));
        AgentRegistry agentRegistry = new AgentRegistry(new ClaudeClient()); // no ANTHROPIC_API_KEY in test env -> exercises the fallback path
        this.engine = new ExecutionEngine(agentRegistry, new FallbackAgentEngine(), governanceEngine,
                new ReplanningEngine(), rollbackRegistry, metrics, auditLogger);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("orchestrator-test-audit");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void await(BooleanSupplier condition, long timeoutMillis, String failureMessage) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new MicroTest.AssertionFailure(failureMessage);
    }

    public static void awaitApprovalPending(Run run, StageId stage, long timeoutMillis) {
        await(() -> run.pendingCheckpoints().stream().anyMatch(cp -> cp.stage() == stage),
                timeoutMillis, "Timed out waiting for a pending approval on stage " + stage);
    }

    public static void awaitTerminal(Run run, long timeoutMillis) {
        await(run::isTerminal, timeoutMillis, "Timed out waiting for run to reach a terminal state; last state=" + run.state());
    }

    public static void awaitState(Run run, RunState state, long timeoutMillis) {
        await(() -> run.state() == state, timeoutMillis, "Timed out waiting for run state=" + state + "; last state=" + run.state());
    }
}

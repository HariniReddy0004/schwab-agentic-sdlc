package com.schwab.orchestrator.execution;

import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.graph.StageStatus;
import com.schwab.orchestrator.model.ApprovalRequest;
import com.schwab.orchestrator.model.CheckpointKind;
import com.schwab.orchestrator.model.RunRequest;
import com.schwab.orchestrator.model.RunState;
import com.schwab.orchestrator.model.ScenarioType;
import com.schwab.orchestrator.testing.EngineTestHarness;
import com.schwab.orchestrator.testing.MicroTest;

import java.util.UUID;

/**
 * Exercises the orchestrator end-to-end with no ANTHROPIC_API_KEY configured (true of this build
 * environment), which means every LLM-backed stage genuinely retries against a real (failing,
 * fast-failing) client and then falls back to the deterministic engine -- these tests are
 * therefore also implicitly testing the retry-then-fallback path on every stage, not just the
 * scenarios that name it explicitly.
 */
public class ExecutionEngineTest {

    private RunRequest req(ScenarioType type, String title, String text) {
        return new RunRequest("run-" + UUID.randomUUID().toString().substring(0, 8), type, title, text, null, "test-harness");
    }

    @MicroTest.Test
    public void greenfieldRunCompletesAfterBothApprovalGates() {
        EngineTestHarness h = new EngineTestHarness();
        Run run = h.engine.startRun(req(ScenarioType.GREENFIELD, "Add expiring links",
                "Add an optional ttlSeconds field so a created short URL automatically stops resolving after N seconds, returning 410 Gone once expired."));

        EngineTestHarness.awaitApprovalPending(run, StageId.IMPLEMENTATION, 15_000);
        MicroTest.assertEquals(RunState.WAITING_APPROVAL, run.state(), "run should be waiting on the implementation entry gate");
        h.engine.decideApproval(run, "IMPLEMENTATION:ENTRY", ApprovalRequest.Decision.APPROVED, "reviewer1", "looks safe");

        EngineTestHarness.awaitApprovalPending(run, StageId.RELEASE_READINESS, 20_000);
        MicroTest.assertEquals(StageStatus.SUCCEEDED, run.statusOf(StageId.TESTING), "testing should have succeeded before release-readiness gate opens");
        MicroTest.assertEquals(StageStatus.SUCCEEDED, run.statusOf(StageId.DOCUMENTATION), "documentation should have succeeded before release-readiness gate opens");
        h.engine.decideApproval(run, "RELEASE_READINESS:EXIT", ApprovalRequest.Decision.APPROVED, "release-manager", "ship it");

        EngineTestHarness.awaitTerminal(run, 10_000);
        MicroTest.assertEquals(RunState.COMPLETED, run.state(), "run should complete once both gates are approved");
        MicroTest.assertTrue(run.context().lineage().size() > 5, "decision lineage should have accumulated entries across stages");
        MicroTest.assertTrue(h.auditLogger.forRun(run.id()).size() > 10, "audit log should have many recorded events");
    }

    @MicroTest.Test
    public void rejectingImplementationApprovalFailsTheRun() {
        EngineTestHarness h = new EngineTestHarness();
        Run run = h.engine.startRun(req(ScenarioType.GREENFIELD, "Risky change", "Add a bulk-delete endpoint that removes many short URLs at once by prefix match."));

        EngineTestHarness.awaitApprovalPending(run, StageId.IMPLEMENTATION, 15_000);
        h.engine.decideApproval(run, "IMPLEMENTATION:ENTRY", ApprovalRequest.Decision.REJECTED, "reviewer1", "too risky without a confirmation step");

        EngineTestHarness.awaitTerminal(run, 5_000);
        MicroTest.assertEquals(RunState.FAILED, run.state(), "run should fail when a human rejects the implementation gate");
        MicroTest.assertEquals(StageStatus.FAILED, run.statusOf(StageId.IMPLEMENTATION), "implementation stage should be marked failed on rejection");
    }

    @MicroTest.Test
    public void securityGuardrailBlocksDeniedRequirement() {
        EngineTestHarness h = new EngineTestHarness();
        Run run = h.engine.startRun(req(ScenarioType.GREENFIELD, "Bad idea",
                "For debugging convenience, disable authentication on the analytics endpoint until further notice."));

        EngineTestHarness.awaitApprovalPending(run, StageId.IMPLEMENTATION, 15_000);
        h.engine.decideApproval(run, "IMPLEMENTATION:ENTRY", ApprovalRequest.Decision.APPROVED, "reviewer1", "approved without reading closely");

        EngineTestHarness.awaitTerminal(run, 10_000);
        MicroTest.assertEquals(RunState.BLOCKED_GUARDRAIL, run.state(), "security guardrail should block this requirement even after human approval");
        MicroTest.assertEquals(StageStatus.BLOCKED_GUARDRAIL, run.statusOf(StageId.IMPLEMENTATION), "implementation stage should be marked blocked");
    }

    @MicroTest.Test
    public void ambiguousRequirementTriggersClarificationReplan() {
        EngineTestHarness h = new EngineTestHarness();
        Run run = h.engine.startRun(req(ScenarioType.AMBIGUOUS, "Make it better", "Make the redirect faster, somehow."));

        EngineTestHarness.awaitApprovalPending(run, StageId.CLARIFICATION, 15_000);
        MicroTest.assertTrue(run.graph().contains(StageId.CLARIFICATION), "graph should have been re-planned to include CLARIFICATION");
        MicroTest.assertTrue(run.replans().size() >= 1, "a replan event should have been recorded");

        h.engine.decideApproval(run, "CLARIFICATION:ENTRY", ApprovalRequest.Decision.APPROVED, "product-owner",
                "Target p99 redirect latency under 50ms by adding a cache in front of the repository lookup.");

        EngineTestHarness.awaitApprovalPending(run, StageId.IMPLEMENTATION, 15_000);
        MicroTest.assertEquals("Target p99 redirect latency under 50ms by adding a cache in front of the repository lookup.",
                run.context().clarificationAnswer(), "clarification answer should be captured in the execution context");

        h.engine.decideApproval(run, "IMPLEMENTATION:ENTRY", ApprovalRequest.Decision.APPROVED, "reviewer1", "ok given the clarification");
        EngineTestHarness.awaitApprovalPending(run, StageId.RELEASE_READINESS, 20_000);
        h.engine.decideApproval(run, "RELEASE_READINESS:EXIT", ApprovalRequest.Decision.APPROVED, "release-manager", "ship it");
        EngineTestHarness.awaitTerminal(run, 10_000);
        MicroTest.assertEquals(RunState.COMPLETED, run.state(), "run should still be able to complete after a mid-run replan");
    }

    @MicroTest.Test
    public void safeStopHaltsAFreshRunBeforeCompletion() {
        EngineTestHarness h = new EngineTestHarness();
        Run run = h.engine.startRun(req(ScenarioType.GREENFIELD, "Trivial change", "Add a /version endpoint returning the running build's git SHA."));
        h.engine.requestSafeStop(run, "Operator requested halt for maintenance");
        EngineTestHarness.awaitTerminal(run, 10_000);
        MicroTest.assertEquals(RunState.SAFE_STOPPED, run.state(), "run should stop rather than proceed to completion");
    }

    @MicroTest.Test
    public void rejectsBlankClarificationAndDuplicateTerminalDecision() {
        EngineTestHarness h = new EngineTestHarness();
        Run ambiguous = h.engine.startRun(req(ScenarioType.AMBIGUOUS, "Unclear", "Make redirects better."));
        EngineTestHarness.awaitApprovalPending(ambiguous, StageId.CLARIFICATION, 15_000);
        MicroTest.assertThrows(IllegalArgumentException.class,
                () -> h.engine.decideApproval(ambiguous, "CLARIFICATION:ENTRY", ApprovalRequest.Decision.APPROVED, "owner", " "),
                "clarification approval should require a concrete answer");
        MicroTest.assertTrue(ambiguous.pendingCheckpoints().stream().anyMatch(cp -> "CLARIFICATION:ENTRY".equals(cp.checkpointId())), "invalid clarification must leave the checkpoint pending for correction");
        Run rejected = h.engine.startRun(req(ScenarioType.GREENFIELD, "Risky", "Add a bulk-delete endpoint that removes matching inactive links " + "after validating an explicit confirmation token."));
        EngineTestHarness.awaitApprovalPending(rejected, StageId.IMPLEMENTATION, 15_000);
        h.engine.decideApproval(rejected, "IMPLEMENTATION:ENTRY", ApprovalRequest.Decision.REJECTED, "reviewer", "not approved");
        EngineTestHarness.awaitTerminal(rejected, 5_000);
        MicroTest.assertThrows(IllegalStateException.class,
                () -> h.engine.decideApproval(rejected, "IMPLEMENTATION:ENTRY", ApprovalRequest.Decision.APPROVED, "reviewer", "changed mind"),
                "terminal runs must reject later approval mutations");
    }
}

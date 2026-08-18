package com.schwab.orchestrator.governance;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.graph.StageStatus;
import com.schwab.orchestrator.model.RunRequest;
import com.schwab.orchestrator.model.ScenarioType;
import com.schwab.orchestrator.model.StageOutput;
import com.schwab.orchestrator.testing.MicroTest;

import java.time.Instant;
import java.util.Map;

public class ComplianceGuardrailTest {

    private RunRequest req() {
        return new RunRequest("run-x", ScenarioType.GREENFIELD, "t", "requirement text", null, "tester");
    }

    @MicroTest.Test
    public void blocksReleaseWithoutTestArtifacts() {
        ExecutionContext ctx = new ExecutionContext(req());
        StageOutput docs = new StageOutput(StageId.DOCUMENTATION, 1, Instant.now());
        docs.setArtifacts(Map.of("documentation", "some docs"));
        ctx.recordOutput(docs);
        // no TESTING output recorded at all

        GuardrailResult result = new ComplianceGuardrail().evaluate(StageId.RELEASE_READINESS, ctx, req());
        MicroTest.assertFalse(result.allowed(), "release should be blocked without any testing output");
    }

    @MicroTest.Test
    public void blocksReleaseWithEmptyTestArtifacts() {
        ExecutionContext ctx = new ExecutionContext(req());
        StageOutput testing = new StageOutput(StageId.TESTING, 1, Instant.now());
        testing.setArtifacts(Map.of()); // present but empty
        ctx.recordOutput(testing);
        StageOutput docs = new StageOutput(StageId.DOCUMENTATION, 1, Instant.now());
        docs.setArtifacts(Map.of("documentation", "some docs"));
        ctx.recordOutput(docs);

        GuardrailResult result = new ComplianceGuardrail().evaluate(StageId.RELEASE_READINESS, ctx, req());
        MicroTest.assertFalse(result.allowed(), "release should be blocked when testing artifacts map is empty");
    }

    @MicroTest.Test
    public void allowsReleaseWhenBothPresent() {
        ExecutionContext ctx = new ExecutionContext(req());
        StageOutput testing = new StageOutput(StageId.TESTING, 1, Instant.now());
        testing.setArtifacts(Map.of("testPlan", "..."));
        ctx.recordOutput(testing);
        StageOutput docs = new StageOutput(StageId.DOCUMENTATION, 1, Instant.now());
        docs.setArtifacts(Map.of("documentation", "..."));
        ctx.recordOutput(docs);

        GuardrailResult result = new ComplianceGuardrail().evaluate(StageId.RELEASE_READINESS, ctx, req());
        MicroTest.assertTrue(result.allowed(), "release should be allowed once both testing and documentation artifacts exist");
    }

    @MicroTest.Test
    public void ignoresNonReleaseStages() {
        ExecutionContext ctx = new ExecutionContext(req());
        GuardrailResult result = new ComplianceGuardrail().evaluate(StageId.IMPLEMENTATION, ctx, req());
        MicroTest.assertTrue(result.allowed(), "compliance guardrail should not apply to non-release stages");
    }
}

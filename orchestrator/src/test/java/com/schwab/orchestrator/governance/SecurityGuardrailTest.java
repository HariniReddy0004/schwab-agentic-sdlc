package com.schwab.orchestrator.governance;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;
import com.schwab.orchestrator.model.ScenarioType;
import com.schwab.orchestrator.testing.MicroTest;

public class SecurityGuardrailTest {

    private RunRequest req(String text) {
        return new RunRequest("run-x", ScenarioType.GREENFIELD, "t", text, null, "tester");
    }

    @MicroTest.Test
    public void blocksDeniedPattern() {
        RunRequest r = req("Please disable authentication for the internal dashboard temporarily.");
        GuardrailResult result = new SecurityGuardrail().evaluate(StageId.IMPLEMENTATION, new ExecutionContext(r), r);
        MicroTest.assertFalse(result.allowed(), "should block a requirement asking to disable authentication");
    }

    @MicroTest.Test
    public void allowsBenignRequirement() {
        RunRequest r = req("Add a customAlias field to the short URL creation endpoint.");
        GuardrailResult result = new SecurityGuardrail().evaluate(StageId.IMPLEMENTATION, new ExecutionContext(r), r);
        MicroTest.assertTrue(result.allowed(), "benign requirement should not be blocked");
    }

    @MicroTest.Test
    public void onlyAppliesToImplementationStage() {
        RunRequest r = req("Please disable authentication for the internal dashboard temporarily.");
        GuardrailResult result = new SecurityGuardrail().evaluate(StageId.DOCUMENTATION, new ExecutionContext(r), r);
        MicroTest.assertTrue(result.allowed(), "security guardrail should only gate the IMPLEMENTATION stage");
    }
}

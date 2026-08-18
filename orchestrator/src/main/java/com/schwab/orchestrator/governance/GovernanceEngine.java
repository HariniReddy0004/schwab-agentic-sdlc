package com.schwab.orchestrator.governance;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;

import java.util.ArrayList;
import java.util.List;

/** Runs every registered guardrail for a candidate stage; any single BLOCK halts that stage. */
public final class GovernanceEngine {
    private final List<PolicyGuardrail> guardrails;

    public GovernanceEngine(List<PolicyGuardrail> guardrails) {
        this.guardrails = guardrails;
    }

    public List<GuardrailResult> evaluateAll(StageId stage, ExecutionContext context, RunRequest request) {
        List<GuardrailResult> results = new ArrayList<>();
        for (PolicyGuardrail g : guardrails) {
            results.add(g.evaluate(stage, context, request));
        }
        return results;
    }

    public boolean anyBlocking(List<GuardrailResult> results) {
        return results.stream().anyMatch(r -> !r.allowed());
    }
}

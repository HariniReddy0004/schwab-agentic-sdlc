package com.schwab.orchestrator.governance;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;
import com.schwab.orchestrator.model.StageOutput;

/**
 * Enforces a documentation/testing completeness policy before release: RELEASE_READINESS may not
 * proceed unless both TESTING and DOCUMENTATION produced non-empty artifacts. This models a
 * change-control policy ("nothing ships undocumented or untested") as code rather than as a
 * checklist a human might skip under deadline pressure.
 */
public final class ComplianceGuardrail implements PolicyGuardrail {

    @Override
    public String name() {
        return "compliance-guardrail";
    }

    @Override
    public GuardrailResult evaluate(StageId stage, ExecutionContext context, RunRequest request) {
        if (stage != StageId.RELEASE_READINESS) return GuardrailResult.pass(name());

        StageOutput testing = context.outputOf(StageId.TESTING);
        StageOutput docs = context.outputOf(StageId.DOCUMENTATION);
        if (testing == null || testing.artifacts().isEmpty()) {
            return GuardrailResult.block(name(), "Release blocked: TESTING stage produced no test artifacts");
        }
        if (docs == null || docs.artifacts().isEmpty()) {
            return GuardrailResult.block(name(), "Release blocked: DOCUMENTATION stage produced no documentation artifacts");
        }
        return GuardrailResult.pass(name());
    }
}

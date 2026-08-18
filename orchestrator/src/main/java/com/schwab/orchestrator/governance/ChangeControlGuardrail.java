package com.schwab.orchestrator.governance;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;

/**
 * Defense in depth alongside the entry-gate mechanism itself: even if a caller somehow reached
 * this guardrail for IMPLEMENTATION without the run's checkpoint bookkeeping recording an
 * approval, the guardrail independently insists a human decision exists in the lineage before
 * high-impact work proceeds. GovernanceEngine evaluates this after the checkpoint gate, so in
 * normal operation it is redundant by design — that redundancy is the point.
 */
public final class ChangeControlGuardrail implements PolicyGuardrail {

    @Override
    public String name() {
        return "change-control-guardrail";
    }

    @Override
    public GuardrailResult evaluate(StageId stage, ExecutionContext context, RunRequest request) {
        if (stage != StageId.IMPLEMENTATION) return GuardrailResult.pass(name());
        boolean hasHumanApproval = context.lineage().stream()
                .anyMatch(d -> "HUMAN".equals(d.actor()) && d.stage() == StageId.IMPLEMENTATION);
        if (!hasHumanApproval) {
            return GuardrailResult.block(name(), "No recorded human approval decision for IMPLEMENTATION in the decision lineage");
        }
        return GuardrailResult.pass(name());
    }
}

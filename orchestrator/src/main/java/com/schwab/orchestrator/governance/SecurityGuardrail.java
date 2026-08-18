package com.schwab.orchestrator.governance;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;

import java.util.List;
import java.util.Locale;

/**
 * Blocks IMPLEMENTATION from starting if the requirement text asks for something that reads as a
 * security regression. A real system would back this with SAST/secret-scanning and a proper
 * policy engine (OPA/Rego, etc.); this keyword check is a stand-in that demonstrates the
 * guardrail's place in the pipeline (evaluated at the IMPLEMENTATION entry gate, before any code
 * is generated) rather than a production-grade scanner.
 */
public final class SecurityGuardrail implements PolicyGuardrail {
    private static final List<String> DENY_PATTERNS = List.of(
            "disable authentication", "disable auth", "remove encryption", "hardcode secret",
            "hard-code secret", "hardcode api key", "skip input validation", "disable tls", "log passwords in plaintext"
    );

    @Override
    public String name() {
        return "security-guardrail";
    }

    @Override
    public GuardrailResult evaluate(StageId stage, ExecutionContext context, RunRequest request) {
        if (stage != StageId.IMPLEMENTATION) return GuardrailResult.pass(name());
        String text = (request.requirementText() == null ? "" : request.requirementText()).toLowerCase(Locale.ROOT);
        for (String pattern : DENY_PATTERNS) {
            if (text.contains(pattern)) {
                return GuardrailResult.block(name(), "Requirement text matches a denied security pattern: '" + pattern + "'");
            }
        }
        return GuardrailResult.pass(name());
    }
}

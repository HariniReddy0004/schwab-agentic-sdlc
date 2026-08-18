package com.schwab.orchestrator.governance;

public record GuardrailResult(boolean allowed, String guardrailName, String reason) {

    public static GuardrailResult pass(String guardrailName) {
        return new GuardrailResult(true, guardrailName, "ok");
    }

    public static GuardrailResult block(String guardrailName, String reason) {
        return new GuardrailResult(false, guardrailName, reason);
    }
}

package com.schwab.orchestrator.llm;

/** Thrown when no LLM call can be made (no API key configured, or the call failed/timed out). Callers fall back to FallbackAgentEngine. */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

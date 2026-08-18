package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.framework.Json;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.llm.ClaudeClient;
import com.schwab.orchestrator.llm.LlmUnavailableException;
import com.schwab.orchestrator.llm.PromptTemplates;
import com.schwab.orchestrator.model.RunRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default agent for most stages: calls Claude with a stage-specific prompt and parses the
 * required JSON contract out of the response. If no API key is configured, or the call fails,
 * this throws {@link LlmUnavailableException} — the ExecutionEngine is what decides whether to
 * retry or fall back (see reliability policy), keeping that decision out of the agent itself.
 */
public final class LlmBackedAgent implements Agent {
    private final ClaudeClient client;
    private final PromptTemplates prompts = new PromptTemplates();

    public LlmBackedAgent(ClaudeClient client) {
        this.client = client;
    }

    @Override
    public StageAgentResult execute(StageId stage, ExecutionContext context, RunRequest request) {
        PromptTemplates.Prompt prompt = prompts.forStage(stage, context, request);
        String raw = client.complete(prompt.system(), prompt.user(), 1500);
        return parse(raw);
    }

    @SuppressWarnings("unchecked")
    private StageAgentResult parse(String raw) {
        String cleaned = stripMarkdownFence(raw.strip());
        Map<String, Object> obj;
        try {
            obj = Json.parseObject(cleaned);
        } catch (Exception e) {
            throw new LlmUnavailableException("Model response was not valid JSON per the required contract: " + e.getMessage(), e);
        }

        Map<String, String> artifacts = new LinkedHashMap<>();
        Object artifactsObj = obj.get("artifacts");
        if (artifactsObj instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                artifacts.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }

        List<StageAgentResult.Decision> decisions = new ArrayList<>();
        Object decisionsObj = obj.get("decisions");
        if (decisionsObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    decisions.add(new StageAgentResult.Decision(
                            String.valueOf(m.get("description")), String.valueOf(m.get("rationale"))));
                }
            }
        }

        List<String> riskFlags = stringList(obj.get("riskFlags"));
        List<String> clarifyingQuestions = stringList(obj.get("clarifyingQuestions"));

        double confidence = 0.5;
        if (obj.get("confidence") instanceof Number n) confidence = n.doubleValue();

        return new StageAgentResult(
                obj.get("summary") == null ? "" : String.valueOf(obj.get("summary")),
                artifacts,
                decisions,
                riskFlags,
                Boolean.TRUE.equals(obj.get("requiresHumanReview")),
                Boolean.TRUE.equals(obj.get("ambiguous")),
                clarifyingQuestions,
                confidence
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object item : list) out.add(String.valueOf(item));
        return out;
    }

    private String stripMarkdownFence(String s) {
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return s.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return s;
    }
}

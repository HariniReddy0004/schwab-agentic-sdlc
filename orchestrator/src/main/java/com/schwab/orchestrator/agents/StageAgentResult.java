package com.schwab.orchestrator.agents;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What an Agent produces for one stage attempt. Deliberately mirrors the JSON contract the LLM prompts ask for. */
public final class StageAgentResult {
    private final String summary;
    private final Map<String, String> artifacts;
    private final List<Decision> decisions;
    private final List<String> riskFlags;
    private final boolean requiresHumanReview;
    private final boolean ambiguous;
    private final List<String> clarifyingQuestions;
    private final double confidence;

    public record Decision(String description, String rationale) {
    }

    public StageAgentResult(String summary, Map<String, String> artifacts, List<Decision> decisions,
                             List<String> riskFlags, boolean requiresHumanReview, boolean ambiguous,
                             List<String> clarifyingQuestions, double confidence) {
        this.summary = summary;
        this.artifacts = artifacts == null ? Map.of() : Map.copyOf(artifacts);
        this.decisions = decisions == null ? List.of() : List.copyOf(decisions);
        this.riskFlags = riskFlags == null ? List.of() : List.copyOf(riskFlags);
        this.requiresHumanReview = requiresHumanReview;
        this.ambiguous = ambiguous;
        this.clarifyingQuestions = clarifyingQuestions == null ? List.of() : List.copyOf(clarifyingQuestions);
        this.confidence = confidence;
    }

    public String summary() {
        return summary;
    }

    public Map<String, String> artifacts() {
        return artifacts;
    }

    public List<Decision> decisions() {
        return decisions;
    }

    public List<String> riskFlags() {
        return riskFlags;
    }

    public boolean requiresHumanReview() {
        return requiresHumanReview;
    }

    public boolean ambiguous() {
        return ambiguous;
    }

    public List<String> clarifyingQuestions() {
        return clarifyingQuestions;
    }

    public double confidence() {
        return confidence;
    }

    public static Map<String, String> singleArtifact(String name, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(name, content);
        return m;
    }
}

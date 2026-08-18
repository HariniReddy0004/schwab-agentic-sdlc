package com.schwab.orchestrator.model;

/** Normalized input to a run: what was asked for, and (optionally) where the existing codebase lives for brownfield reasoning. */
public final class RunRequest {
    private final String runId;
    private final ScenarioType scenarioType;
    private final String title;
    private final String requirementText;
    private final String repoContextPath; // nullable; used by CodebaseReasoningAgent for brownfield runs
    private final String requestedBy;

    public RunRequest(String runId, ScenarioType scenarioType, String title, String requirementText,
                       String repoContextPath, String requestedBy) {
        this.runId = runId;
        this.scenarioType = scenarioType;
        this.title = title;
        this.requirementText = requirementText;
        this.repoContextPath = repoContextPath;
        this.requestedBy = requestedBy;
    }

    public String runId() {
        return runId;
    }

    public ScenarioType scenarioType() {
        return scenarioType;
    }

    public String title() {
        return title;
    }

    public String requirementText() {
        return requirementText;
    }

    public String repoContextPath() {
        return repoContextPath;
    }

    public String requestedBy() {
        return requestedBy;
    }
}

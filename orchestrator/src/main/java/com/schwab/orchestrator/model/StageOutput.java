package com.schwab.orchestrator.model;

import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.graph.StageStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The recorded result of one stage attempt, kept in the run's context so downstream stages (and humans) can see it. */
public final class StageOutput {
    private final StageId stageId;
    private volatile StageStatus status;
    private final int attempt;
    private final Instant startedAt;
    private volatile Instant finishedAt;
    private volatile String summary;
    private volatile Map<String, String> artifacts = Map.of();
    private volatile List<String> riskFlags = List.of();
    private volatile boolean requiresHumanReview;
    private volatile boolean ambiguous;
    private volatile List<String> clarifyingQuestions = List.of();
    private volatile String errorMessage;
    private volatile boolean usedFallback;

    public StageOutput(StageId stageId, int attempt, Instant startedAt) {
        this.stageId = stageId;
        this.attempt = attempt;
        this.startedAt = startedAt;
        this.status = StageStatus.RUNNING;
    }

    public StageId stageId() {
        return stageId;
    }

    public StageStatus status() {
        return status;
    }

    public void setStatus(StageStatus status) {
        this.status = status;
    }

    public int attempt() {
        return attempt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String summary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, String> artifacts() {
        return artifacts;
    }

    public void setArtifacts(Map<String, String> artifacts) {
        this.artifacts = artifacts;
    }

    public List<String> riskFlags() {
        return riskFlags;
    }

    public void setRiskFlags(List<String> riskFlags) {
        this.riskFlags = riskFlags;
    }

    public boolean requiresHumanReview() {
        return requiresHumanReview;
    }

    public void setRequiresHumanReview(boolean requiresHumanReview) {
        this.requiresHumanReview = requiresHumanReview;
    }

    public boolean ambiguous() {
        return ambiguous;
    }

    public void setAmbiguous(boolean ambiguous) {
        this.ambiguous = ambiguous;
    }

    public List<String> clarifyingQuestions() {
        return clarifyingQuestions;
    }

    public void setClarifyingQuestions(List<String> clarifyingQuestions) {
        this.clarifyingQuestions = clarifyingQuestions;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean usedFallback() {
        return usedFallback;
    }

    public void setUsedFallback(boolean usedFallback) {
        this.usedFallback = usedFallback;
    }

    public long durationMillis() {
        if (finishedAt == null) return -1;
        return finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stageId", stageId.name());
        m.put("status", status.name());
        m.put("attempt", attempt);
        m.put("startedAt", startedAt.toString());
        m.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
        m.put("durationMillis", durationMillis());
        m.put("summary", summary);
        m.put("artifacts", artifacts);
        m.put("riskFlags", riskFlags);
        m.put("requiresHumanReview", requiresHumanReview);
        m.put("ambiguous", ambiguous);
        m.put("clarifyingQuestions", clarifyingQuestions);
        m.put("errorMessage", errorMessage);
        m.put("usedFallback", usedFallback);
        return m;
    }
}

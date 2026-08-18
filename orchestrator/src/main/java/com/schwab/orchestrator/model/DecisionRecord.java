package com.schwab.orchestrator.model;

import com.schwab.orchestrator.graph.StageId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One entry in the run's decision lineage: who/what decided something, why, and when. This is
 * what makes cross-stage context reconstructable after the fact ("audit-grade traceability") —
 * every stage's agent, every guardrail verdict, and every human approval appends one of these.
 */
public record DecisionRecord(Instant timestamp, StageId stage, String actor, String description, String rationale) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", timestamp.toString());
        m.put("stage", stage == null ? null : stage.name());
        m.put("actor", actor);
        m.put("description", description);
        m.put("rationale", rationale);
        return m;
    }
}

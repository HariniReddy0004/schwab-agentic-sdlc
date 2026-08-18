package com.schwab.orchestrator.audit;

import com.schwab.orchestrator.graph.StageId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record AuditEvent(Instant timestamp, String runId, StageId stage, String type, String actor,
                          String message, Map<String, Object> data) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", timestamp.toString());
        m.put("runId", runId);
        m.put("stage", stage == null ? null : stage.name());
        m.put("type", type);
        m.put("actor", actor);
        m.put("message", message);
        m.put("data", data);
        return m;
    }
}

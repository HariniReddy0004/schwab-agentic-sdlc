package com.schwab.orchestrator.execution;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Records a single dynamic re-plan: what triggered it, and what changed about the graph. */
public record ReplanEvent(Instant timestamp, String triggeringStage, String reason, String graphChange) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", timestamp.toString());
        m.put("triggeringStage", triggeringStage);
        m.put("reason", reason);
        m.put("graphChange", graphChange);
        return m;
    }
}

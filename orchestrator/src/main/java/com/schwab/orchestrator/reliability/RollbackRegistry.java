package com.schwab.orchestrator.reliability;

import com.schwab.orchestrator.graph.StageId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maps a stage to the action that undoes it, if it has one. Stages with no registered action are not rollback-capable. */
public final class RollbackRegistry {
    private final Map<StageId, RollbackAction> actions = new ConcurrentHashMap<>();

    public void register(StageId stageId, RollbackAction action) {
        actions.put(stageId, action);
    }

    public boolean supports(StageId stageId) {
        return actions.containsKey(stageId);
    }

    public RollbackAction get(StageId stageId) {
        return actions.get(stageId);
    }
}

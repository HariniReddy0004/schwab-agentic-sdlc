package com.schwab.orchestrator.reliability;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;

/** Reverses the effect of a previously-succeeded stage. Registered per StageId in RollbackRegistry. */
@FunctionalInterface
public interface RollbackAction {
    /** Returns a human-readable description of what was rolled back, for the audit log. */
    String rollback(StageId stageId, ExecutionContext context);
}

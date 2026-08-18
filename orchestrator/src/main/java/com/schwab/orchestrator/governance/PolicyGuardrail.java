package com.schwab.orchestrator.governance;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;

/** A single policy check evaluated before a stage is allowed to run. */
public interface PolicyGuardrail {
    String name();

    GuardrailResult evaluate(StageId stage, ExecutionContext context, RunRequest request);
}

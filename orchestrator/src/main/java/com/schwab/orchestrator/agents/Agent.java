package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;

public interface Agent {
    StageAgentResult execute(StageId stage, ExecutionContext context, RunRequest request);
}

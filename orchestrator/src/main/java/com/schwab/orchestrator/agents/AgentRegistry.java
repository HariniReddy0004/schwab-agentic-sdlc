package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.llm.ClaudeClient;

import java.util.EnumMap;
import java.util.Map;

/** Wires one primary Agent per stage. CODEBASE_REASONING gets real static analysis; everything else is LLM-backed. */
public final class AgentRegistry {
    private final Map<StageId, Agent> agents = new EnumMap<>(StageId.class);

    public AgentRegistry(ClaudeClient claudeClient) {
        Agent llmAgent = new LlmBackedAgent(claudeClient);
        for (StageId stage : StageId.values()) {
            agents.put(stage, llmAgent);
        }
        agents.put(StageId.CODEBASE_REASONING, new CodebaseReasoningAgent());
    }

    public Agent agentFor(StageId stage) {
        Agent agent = agents.get(stage);
        if (agent == null) throw new IllegalArgumentException("No agent registered for stage " + stage);
        return agent;
    }
}

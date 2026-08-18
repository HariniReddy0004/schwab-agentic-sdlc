package com.schwab.orchestrator;

import com.schwab.orchestrator.agents.AgentRegistry;
import com.schwab.orchestrator.agents.FallbackAgentEngine;
import com.schwab.orchestrator.api.OrchestratorController;
import com.schwab.orchestrator.audit.AuditLogger;
import com.schwab.orchestrator.execution.ExecutionEngine;
import com.schwab.orchestrator.framework.Router;
import com.schwab.orchestrator.framework.WebServer;
import com.schwab.orchestrator.governance.ChangeControlGuardrail;
import com.schwab.orchestrator.governance.ComplianceGuardrail;
import com.schwab.orchestrator.governance.GovernanceEngine;
import com.schwab.orchestrator.governance.SecurityGuardrail;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.llm.ClaudeClient;
import com.schwab.orchestrator.reliability.MetricsRegistry;
import com.schwab.orchestrator.reliability.ReplanningEngine;
import com.schwab.orchestrator.reliability.RollbackRegistry;
import com.schwab.orchestrator.store.RunStore;

import java.nio.file.Path;
import java.util.List;

/** Composition root: wires the DAG engine, governance, reliability, LLM client, and REST API together. */
public final class OrchestratorApp {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        ClaudeClient claudeClient = new ClaudeClient();
        AgentRegistry agentRegistry = new AgentRegistry(claudeClient);
        FallbackAgentEngine fallbackAgentEngine = new FallbackAgentEngine();

        GovernanceEngine governanceEngine = new GovernanceEngine(List.of(
                new SecurityGuardrail(),
                new ComplianceGuardrail(),
                new ChangeControlGuardrail()
        ));

        RollbackRegistry rollbackRegistry = new RollbackRegistry();
        // If TESTING fails permanently after IMPLEMENTATION already succeeded, we cannot in good
        // conscience leave unverified code marked as done: roll the implementation back to "needs rework".
        rollbackRegistry.register(StageId.IMPLEMENTATION, (stageId, ctx) ->
                "Reverted IMPLEMENTATION to a not-done state because downstream TESTING failed permanently; " +
                        "the change must be re-implemented and re-tested before it can proceed.");

        MetricsRegistry metrics = new MetricsRegistry();
        AuditLogger auditLogger = new AuditLogger(Path.of(System.getenv().getOrDefault("AUDIT_LOG_DIR", "audit-log")));
        ReplanningEngine replanningEngine = new ReplanningEngine();

        ExecutionEngine engine = new ExecutionEngine(agentRegistry, fallbackAgentEngine, governanceEngine,
                replanningEngine, rollbackRegistry, metrics, auditLogger);
        RunStore runStore = new RunStore();

        Router router = new Router();
        router.onAccess(event -> System.out.println("[access] " + event));
        new OrchestratorController(engine, runStore, auditLogger, metrics).register(router);

        WebServer server = new WebServer(port, router);
        server.start();

        System.out.println("orchestrator listening on port " + server.port());
        System.out.println("ANTHROPIC_API_KEY configured: " + claudeClient.isConfigured() +
                " (when false, all LLM-backed stages use the deterministic fallback agent)");
    }
}

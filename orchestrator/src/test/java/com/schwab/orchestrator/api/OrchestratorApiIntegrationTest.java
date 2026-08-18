package com.schwab.orchestrator.api;

import com.schwab.orchestrator.audit.AuditLogger;
import com.schwab.orchestrator.execution.ExecutionEngine;
import com.schwab.orchestrator.framework.Json;
import com.schwab.orchestrator.framework.Router;
import com.schwab.orchestrator.framework.WebServer;
import com.schwab.orchestrator.governance.ChangeControlGuardrail;
import com.schwab.orchestrator.governance.ComplianceGuardrail;
import com.schwab.orchestrator.governance.GovernanceEngine;
import com.schwab.orchestrator.governance.SecurityGuardrail;
import com.schwab.orchestrator.agents.AgentRegistry;
import com.schwab.orchestrator.agents.FallbackAgentEngine;
import com.schwab.orchestrator.llm.ClaudeClient;
import com.schwab.orchestrator.reliability.MetricsRegistry;
import com.schwab.orchestrator.reliability.ReplanningEngine;
import com.schwab.orchestrator.reliability.RollbackRegistry;
import com.schwab.orchestrator.store.RunStore;
import com.schwab.orchestrator.testing.MicroTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/** Drives the real HTTP surface end-to-end: start a run over REST, poll status, approve both gates, read metrics/audit/lineage back. */
public class OrchestratorApiIntegrationTest {

    private WebServer bootServer() throws Exception {
        AuditLogger auditLogger = new AuditLogger(Files.createTempDirectory("orchestrator-api-test"));
        MetricsRegistry metrics = new MetricsRegistry();
        RollbackRegistry rollbackRegistry = new RollbackRegistry();
        GovernanceEngine governanceEngine = new GovernanceEngine(List.of(
                new SecurityGuardrail(), new ComplianceGuardrail(), new ChangeControlGuardrail()));
        AgentRegistry agentRegistry = new AgentRegistry(new ClaudeClient());
        ExecutionEngine engine = new ExecutionEngine(agentRegistry, new FallbackAgentEngine(), governanceEngine,
                new ReplanningEngine(), rollbackRegistry, metrics, auditLogger);
        RunStore store = new RunStore();

        Router router = new Router();
        new OrchestratorController(engine, store, auditLogger, metrics).register(router);
        WebServer server = new WebServer(0, router);
        server.start();
        return server;
    }

    @SuppressWarnings("unchecked")
    @MicroTest.Test
    public void fullRunLifecycleOverHttp() throws Exception {
        WebServer server = bootServer();
        try {
            HttpClient client = HttpClient.newHttpClient();
            int port = server.port();
            String base = "http://localhost:" + port;

            String createBody = Json.write(Map.of(
                    "scenarioType", "GREENFIELD",
                    "title", "Add favicon passthrough",
                    "requirementText", "Add a GET /favicon.ico route that returns 204 No Content so browsers stop 404-ing on it.",
                    "requestedBy", "integration-test"));
            HttpResponse<String> createResp = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/runs"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(createBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            MicroTest.assertEquals(201, createResp.statusCode(), "starting a run should return 201");
            Map<String, Object> created = Json.parseObject(createResp.body());
            String runId = (String) created.get("runId");
            MicroTest.assertNotNull(runId, "created run should have a runId");

            waitForPendingApproval(client, base, runId, "IMPLEMENTATION:ENTRY", 15_000);

            String approveBody = Json.write(Map.of("decision", "APPROVED", "approver", "reviewer1", "comment", "ok"));
            HttpResponse<String> approveResp = client.send(HttpRequest.newBuilder(
                    URI.create(base + "/api/v1/runs/" + runId + "/approvals/IMPLEMENTATION:ENTRY"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(approveBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            MicroTest.assertEquals(200, approveResp.statusCode(), "approving a real pending checkpoint should return 200");

            waitForPendingApproval(client, base, runId, "RELEASE_READINESS:EXIT", 20_000);
            client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/runs/" + runId + "/approvals/RELEASE_READINESS:EXIT"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(approveBody)).build(),
                    HttpResponse.BodyHandlers.ofString());

            Map<String, Object> finalStatus = waitForTerminalState(client, base, runId, 10_000);
            MicroTest.assertEquals("COMPLETED", finalStatus.get("state"), "run should complete over the HTTP API");

            HttpResponse<String> auditResp = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/runs/" + runId + "/audit")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> auditBody = Json.parseObject(auditResp.body());
            MicroTest.assertTrue(((List<Object>) auditBody.get("events")).size() > 5, "audit trail should be retrievable over HTTP and non-trivial");

            HttpResponse<String> lineageResp = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/runs/" + runId + "/lineage")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            MicroTest.assertEquals(200, lineageResp.statusCode(), "lineage endpoint should be reachable");

            HttpResponse<String> metricsResp = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/metrics")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> metricsBody = Json.parseObject(metricsResp.body());
            MicroTest.assertTrue(((Number) metricsBody.get("runsSucceeded")).longValue() >= 1, "global metrics should reflect the completed run");
        } finally {
            server.stop();
        }
    }

    @MicroTest.Test
    public void unknownScenarioTypeReturns400() throws Exception {
        WebServer server = bootServer();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String body = Json.write(Map.of("scenarioType", "NOT_A_REAL_TYPE", "requirementText", "x"));
            HttpResponse<String> resp = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/v1/runs"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            MicroTest.assertEquals(400, resp.statusCode(), "unknown scenario type should 400");
        } finally {
            server.stop();
        }
    }

    private void waitForPendingApproval(HttpClient client, String base, String runId, String checkpointId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> status = getRun(client, base, runId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pending = (List<Map<String, Object>>) status.get("pendingApprovals");
            if (pending.stream().anyMatch(p -> checkpointId.equals(p.get("checkpointId")))) return;
            Thread.sleep(50);
        }
        throw new MicroTest.AssertionFailure("Timed out waiting for pending approval " + checkpointId + " over HTTP");
    }

    private Map<String, Object> waitForTerminalState(HttpClient client, String base, String runId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> status = getRun(client, base, runId);
            String state = (String) status.get("state");
            if (List.of("COMPLETED", "FAILED", "ROLLED_BACK", "SAFE_STOPPED", "BLOCKED_GUARDRAIL").contains(state)) return status;
            Thread.sleep(50);
        }
        throw new MicroTest.AssertionFailure("Timed out waiting for terminal state over HTTP");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getRun(HttpClient client, String base, String runId) throws Exception {
        HttpResponse<String> resp = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/runs/" + runId)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return Json.parseObject(resp.body());
    }
}

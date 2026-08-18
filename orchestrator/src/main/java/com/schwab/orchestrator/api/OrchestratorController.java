package com.schwab.orchestrator.api;

import com.schwab.orchestrator.audit.AuditEvent;
import com.schwab.orchestrator.audit.AuditLogger;
import com.schwab.orchestrator.execution.ExecutionEngine;
import com.schwab.orchestrator.execution.Run;
import com.schwab.orchestrator.framework.ApiException;
import com.schwab.orchestrator.framework.HttpCtx;
import com.schwab.orchestrator.framework.Json;
import com.schwab.orchestrator.framework.Router;
import com.schwab.orchestrator.graph.StageDefinition;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.ApprovalRequest;
import com.schwab.orchestrator.model.RunRequest;
import com.schwab.orchestrator.model.ScenarioType;
import com.schwab.orchestrator.reliability.MetricsRegistry;
import com.schwab.orchestrator.store.RunStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** REST surface for the orchestrator: start runs, inspect state, resolve approvals, safe-stop, read metrics/audit/lineage. */
public final class OrchestratorController {
    private final ExecutionEngine engine;
    private final RunStore store;
    private final AuditLogger auditLogger;
    private final MetricsRegistry metrics;

    public OrchestratorController(ExecutionEngine engine, RunStore store, AuditLogger auditLogger, MetricsRegistry metrics) {
        this.engine = engine;
        this.store = store;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
    }

    public void register(Router router) {
        router.get("/health", ctx -> ctx.sendJson(200, Map.of("status", "UP", "activeRuns", store.all().size())));
        router.post("/api/v1/runs", this::startRun);
        router.get("/api/v1/runs", this::listRuns);
        router.get("/api/v1/runs/{runId}", this::getRun);
        router.get("/api/v1/runs/{runId}/graph", this::getGraph);
        router.get("/api/v1/runs/{runId}/audit", this::getAudit);
        router.get("/api/v1/runs/{runId}/lineage", this::getLineage);
        router.get("/api/v1/runs/{runId}/outputs", this::getOutputs);
        router.post("/api/v1/runs/{runId}/approvals/{checkpointId}", this::decideApproval);
        router.post("/api/v1/runs/{runId}/safe-stop", this::safeStop);
        router.get("/api/v1/metrics", ctx -> ctx.sendJson(200, metrics.snapshot()));
    }

    private void startRun(HttpCtx ctx) {
        Map<String, Object> body = ctx.bodyAsJson();
        String scenarioRaw = Json.str(body, "scenarioType");
        if (scenarioRaw == null) throw ApiException.badRequest("scenarioType is required (GREENFIELD, BROWNFIELD, or AMBIGUOUS)");
        ScenarioType scenarioType;
        try {
            scenarioType = ScenarioType.valueOf(scenarioRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown scenarioType '" + scenarioRaw + "'; expected GREENFIELD, BROWNFIELD, or AMBIGUOUS");
        }
        String title = Json.str(body, "title", "Untitled requirement");
        String requirementText = Json.str(body, "requirementText");
        if (requirementText == null || requirementText.isBlank()) throw ApiException.badRequest("requirementText is required");
        if (requirementText.length() > 20_000) throw ApiException.badRequest("requirementText exceeds maximum length of 20000 characters");
        if (title.isBlank()) throw ApiException.badRequest("title must not be blank");
        if (title.length() > 200) throw ApiException.badRequest("title exceeds maximum length of 200 characters");
        String repoContextPath = Json.str(body, "repoContextPath");
        String requestedBy = Json.str(body, "requestedBy", "unknown");

        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        RunRequest request = new RunRequest(runId, scenarioType, title, requirementText, repoContextPath, requestedBy);
        Run run = engine.startRun(request);
        store.save(run);

        ctx.sendJson(201, summarize(run));
    }

    private void listRuns(HttpCtx ctx) {
        List<Map<String, Object>> all = store.all().stream().map(this::summarize).toList();
        ctx.sendJson(200, Map.of("runs", all));
    }

    private void getRun(HttpCtx ctx) {
        Run run = requireRun(ctx);
        Map<String, Object> body = new LinkedHashMap<>(summarize(run));
        Map<String, Object> statuses = new LinkedHashMap<>();
        run.allStatuses().forEach((id, status) -> statuses.put(id.name(), status.name()));
        body.put("stageStatuses", statuses);
        body.put("pendingApprovals", run.pendingCheckpoints().stream().map(ApprovalRequest::toMap).toList());
        body.put("allCheckpoints", run.allCheckpoints().stream().map(ApprovalRequest::toMap).toList());
        body.put("replans", run.replans().stream().map(com.schwab.orchestrator.execution.ReplanEvent::toMap).toList());
        ctx.sendJson(200, body);
    }

    private void getGraph(HttpCtx ctx) {
        Run run = requireRun(ctx);
        List<Map<String, Object>> nodes = run.graph().allStageIds().stream().map(id -> {
            StageDefinition def = run.graph().get(id);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", id.name());
            node.put("displayName", def.displayName());
            node.put("dependsOn", def.dependsOn().stream().map(StageId::name).toList());
            node.put("entryGate", def.entryGate().name());
            node.put("exitGate", def.exitGate().name());
            node.put("maxAttempts", def.maxAttempts());
            node.put("status", run.statusOf(id).name());
            return node;
        }).toList();
        List<List<String>> batches = run.graph().topologicalBatches().stream()
                .map(batch -> batch.stream().map(StageId::name).toList()).toList();
        ctx.sendJson(200, Map.of("nodes", nodes, "parallelBatches", batches));
    }

    private void getAudit(HttpCtx ctx) {
        Run run = requireRun(ctx);
        List<Map<String, Object>> events = auditLogger.forRun(run.id()).stream().map(AuditEvent::toMap).toList();
        ctx.sendJson(200, Map.of("runId", run.id(), "events", events));
    }

    private void getLineage(HttpCtx ctx) {
        Run run = requireRun(ctx);
        ctx.sendJson(200, Map.of("runId", run.id(),
                "decisions", run.context().lineage().stream().map(com.schwab.orchestrator.model.DecisionRecord::toMap).toList()));
    }

    private void getOutputs(HttpCtx ctx) {
        Run run = requireRun(ctx);
        Map<String, Object> outputs = new LinkedHashMap<>();
        run.context().allOutputs().forEach((id, output) -> outputs.put(id.name(), output.toMap()));
        ctx.sendJson(200, Map.of("runId", run.id(), "outputs", outputs));
    }

    private void decideApproval(HttpCtx ctx) {
        Run run = requireRun(ctx);
        String checkpointId = ctx.pathParam("checkpointId");
        Map<String, Object> body = ctx.bodyAsJson();
        String decisionRaw = Json.str(body, "decision");
        if (decisionRaw == null) throw ApiException.badRequest("decision is required (APPROVED or REJECTED)");
        ApprovalRequest.Decision decision;
        try {
            decision = ApprovalRequest.Decision.valueOf(decisionRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("decision must be APPROVED or REJECTED");
        }
        if (decision == ApprovalRequest.Decision.PENDING) throw ApiException.badRequest("decision must be APPROVED or REJECTED");
        String approver = Json.str(body, "approver");
        if (approver == null || approver.isBlank()) throw ApiException.badRequest("approver is required");
        String comment = Json.str(body, "comment");

        try {
            engine.decideApproval(run, checkpointId, decision, approver, comment);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound(e.getMessage());
        } catch (IllegalStateException e) {
            throw ApiException.conflict(e.getMessage());
        }
        ctx.sendJson(200, summarize(run));
    }

    private void safeStop(HttpCtx ctx) {
        Run run = requireRun(ctx);
        Map<String, Object> body = ctx.bodyAsJson();
        String reason = Json.str(body, "reason", "Safe stop requested via API");
        engine.requestSafeStop(run, reason);
        ctx.sendJson(200, summarize(run));
    }

    private Run requireRun(HttpCtx ctx) {
        String runId = ctx.pathParam("runId");
        return store.find(runId).orElseThrow(() -> ApiException.notFound("No run found with id " + runId));
    }

    private Map<String, Object> summarize(Run run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", run.id());
        m.put("scenarioType", run.request().scenarioType().name());
        m.put("title", run.request().title());
        m.put("state", run.state().name());
        m.put("createdAt", run.createdAt().toString());
        m.put("finishedAt", run.finishedAt() == null ? null : run.finishedAt().toString());
        m.put("terminalReason", run.terminalReason());
        m.put("pendingApprovalCount", run.pendingCheckpoints().size());
        return m;
    }
}

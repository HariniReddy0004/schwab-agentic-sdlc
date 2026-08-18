package com.schwab.orchestrator.audit;

import com.schwab.orchestrator.framework.Json;
import com.schwab.orchestrator.graph.StageId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Append-only audit trail: every governance decision, retry, rollback, approval, and stage
 * transition is recorded here, both in memory (for the API to serve back to clients) and as a
 * JSONL file per run under {@code audit-log/} so the trail survives process restarts and can be
 * grepped/diffed like any other log — "audit-grade observability and traceability".
 */
public final class AuditLogger {
    private final Path logDir;
    private final Map<String, List<AuditEvent>> inMemory = new ConcurrentHashMap<>();

    public AuditLogger(Path logDir) {
        this.logDir = logDir;
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create audit log directory " + logDir, e);
        }
    }

    public void log(String runId, StageId stage, String type, String actor, String message, Map<String, Object> data) {
        AuditEvent event = new AuditEvent(Instant.now(), runId, stage, type, actor, message, data == null ? Map.of() : data);
        inMemory.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(event);
        appendToFile(event);
    }

    private void appendToFile(AuditEvent event) {
        Path file = logDir.resolve(event.runId() + ".jsonl");
        String line = Json.write(event.toMap()) + System.lineSeparator();
        try {
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Audit logging must never crash the orchestration loop; surface loudly to stderr instead.
            System.err.println("WARNING: failed to write audit log entry for run " + event.runId() + ": " + e.getMessage());
        }
    }

    public List<AuditEvent> forRun(String runId) {
        return List.copyOf(inMemory.getOrDefault(runId, List.of()));
    }
}

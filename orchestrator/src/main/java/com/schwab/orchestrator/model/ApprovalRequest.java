package com.schwab.orchestrator.model;

import com.schwab.orchestrator.graph.StageId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** A pending (or resolved) human approval checkpoint blocking a stage's entry or exit. */
public final class ApprovalRequest {
    public enum Decision {PENDING, APPROVED, REJECTED}

    private final String checkpointId; // e.g. "IMPLEMENTATION:ENTRY"
    private final StageId stage;
    private final CheckpointKind kind;
    private final String reason;
    private final Instant requestedAt;
    private final AtomicReference<Decision> decision = new AtomicReference<>(Decision.PENDING);
    private volatile String decidedBy;
    private volatile String comment;
    private volatile Instant decidedAt;

    public ApprovalRequest(String checkpointId, StageId stage, CheckpointKind kind, String reason, Instant requestedAt) {
        this.checkpointId = checkpointId;
        this.stage = stage;
        this.kind = kind;
        this.reason = reason;
        this.requestedAt = requestedAt;
    }

    public String checkpointId() {
        return checkpointId;
    }

    public StageId stage() {
        return stage;
    }

    public CheckpointKind kind() {
        return kind;
    }

    public String reason() {
        return reason;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Decision decision() {
        return decision.get();
    }

    public boolean isPending() {
        return decision.get() == Decision.PENDING;
    }

    /** Returns true if this call actually resolved the request (false if it was already decided). */
    public boolean resolve(Decision outcome, String decidedBy, String comment) {
        boolean applied = decision.compareAndSet(Decision.PENDING, outcome);
        if (applied) {
            this.decidedBy = decidedBy;
            this.comment = comment;
            this.decidedAt = Instant.now();
        }
        return applied;
    }

    public String decidedBy() {
        return decidedBy;
    }

    public String comment() {
        return comment;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checkpointId", checkpointId);
        m.put("stage", stage.name());
        m.put("kind", kind.name());
        m.put("reason", reason);
        m.put("requestedAt", requestedAt.toString());
        m.put("decision", decision.get().name());
        m.put("decidedBy", decidedBy);
        m.put("comment", comment);
        m.put("decidedAt", decidedAt == null ? null : decidedAt.toString());
        return m;
    }
}

package com.schwab.orchestrator.graph;

import java.util.List;
import java.util.Set;

/**
 * Static metadata for one node in the SDLC dependency graph: what it depends on, what governance
 * gates guard it, how it may fail-safe (retries, rollback targets).
 */
public final class StageDefinition {
    private final StageId id;
    private final String displayName;
    private final Set<StageId> dependsOn;
    private final GateType entryGate;
    private final GateType exitGate;
    private final int maxAttempts;
    private final List<StageId> rollbackTargets; // already-succeeded stages to roll back if this stage fails permanently

    public StageDefinition(StageId id, String displayName, Set<StageId> dependsOn, GateType entryGate,
                            GateType exitGate, int maxAttempts, List<StageId> rollbackTargets) {
        this.id = id;
        this.displayName = displayName;
        this.dependsOn = dependsOn;
        this.entryGate = entryGate;
        this.exitGate = exitGate;
        this.maxAttempts = maxAttempts;
        this.rollbackTargets = rollbackTargets;
    }

    public StageId id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Set<StageId> dependsOn() {
        return dependsOn;
    }

    public GateType entryGate() {
        return entryGate;
    }

    public GateType exitGate() {
        return exitGate;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public List<StageId> rollbackTargets() {
        return rollbackTargets;
    }
}

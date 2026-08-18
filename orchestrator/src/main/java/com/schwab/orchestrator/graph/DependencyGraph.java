package com.schwab.orchestrator.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An explicit, mutable DAG of {@link StageDefinition}s. Mutability is deliberate: the
 * ReplanningEngine inserts stages (e.g. CLARIFICATION) mid-run in response to upstream agent
 * output, which is the "dynamically re-plan" requirement — a fixed graph could not do this.
 */
public final class DependencyGraph {
    private final Map<StageId, StageDefinition> stages = new LinkedHashMap<>();

    public void addStage(StageDefinition def) {
        stages.put(def.id(), def);
        validateAcyclic();
    }

    /** Replaces an existing stage's definition (e.g. to rewire its dependencies during a re-plan). */
    public void replaceStage(StageDefinition def) {
        if (!stages.containsKey(def.id())) {
            throw new IllegalArgumentException("Cannot replace unknown stage: " + def.id());
        }
        stages.put(def.id(), def);
        validateAcyclic();
    }

    public boolean contains(StageId id) {
        return stages.containsKey(id);
    }

    public StageDefinition get(StageId id) {
        StageDefinition def = stages.get(id);
        if (def == null) throw new IllegalArgumentException("Unknown stage: " + id);
        return def;
    }

    public Set<StageId> allStageIds() {
        return stages.keySet();
    }

    public Set<StageId> dependenciesOf(StageId id) {
        return get(id).dependsOn();
    }

    public Set<StageId> dependentsOf(StageId id) {
        Set<StageId> out = new LinkedHashSet<>();
        for (StageDefinition def : stages.values()) {
            if (def.dependsOn().contains(id)) out.add(def.id());
        }
        return out;
    }

    /**
     * Kahn's-algorithm topological batching: each returned batch contains stages whose
     * dependencies are all in earlier batches, so stages within one batch are independent of each
     * other and are the orchestrator's legal parallel-execution candidates at that point in time.
     */
    public List<Set<StageId>> topologicalBatches() {
        Map<StageId, Integer> remainingDeps = new LinkedHashMap<>();
        for (StageDefinition def : stages.values()) {
            remainingDeps.put(def.id(), def.dependsOn().size());
        }
        List<Set<StageId>> batches = new ArrayList<>();
        Set<StageId> settled = new LinkedHashSet<>();
        while (settled.size() < stages.size()) {
            Set<StageId> batch = new LinkedHashSet<>();
            for (StageId id : stages.keySet()) {
                if (settled.contains(id)) continue;
                boolean depsSettled = settled.containsAll(get(id).dependsOn());
                if (depsSettled) batch.add(id);
            }
            if (batch.isEmpty()) {
                throw new IllegalStateException("Cycle detected or unresolved dependency among: " +
                        stages.keySet().stream().filter(s -> !settled.contains(s)).toList());
            }
            batches.add(batch);
            settled.addAll(batch);
        }
        return batches;
    }

    private void validateAcyclic() {
        // topologicalBatches() throws IllegalStateException if it cannot make progress, i.e. a cycle exists.
        topologicalBatches();
    }
}

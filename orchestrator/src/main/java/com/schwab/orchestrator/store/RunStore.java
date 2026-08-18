package com.schwab.orchestrator.store;

import com.schwab.orchestrator.execution.Run;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory registry of runs, keyed by run id. Audit trails are separately persisted to disk by AuditLogger. */
public final class RunStore {
    private final ConcurrentMap<String, Run> runs = new ConcurrentHashMap<>();

    public void save(Run run) {
        runs.put(run.id(), run);
    }

    public Optional<Run> find(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    public List<Run> all() {
        return List.copyOf(runs.values());
    }
}

package com.schwab.orchestrator.execution;

import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.DecisionRecord;
import com.schwab.orchestrator.model.RunRequest;
import com.schwab.orchestrator.model.StageOutput;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The run's shared "blackboard": every stage reads prior stages' outputs from here, and every
 * decision (by an agent, a guardrail, or a human) is appended to the lineage list. This is what
 * "preserve cross-stage context and decision lineage" means concretely — it is not re-derived
 * after the fact from logs, it is the live state agents condition on while the run executes.
 */
public final class ExecutionContext {
    private final RunRequest request;
    private final Map<StageId, StageOutput> outputs = new ConcurrentHashMap<>();
    private final List<DecisionRecord> lineage = new CopyOnWriteArrayList<>();
    /** Answers supplied by a human at a CLARIFICATION checkpoint, keyed by the question index. */
    private volatile String clarificationAnswer;

    public ExecutionContext(RunRequest request) {
        this.request = request;
    }

    public RunRequest request() {
        return request;
    }

    public void recordOutput(StageOutput output) {
        outputs.put(output.stageId(), output);
    }

    public StageOutput outputOf(StageId id) {
        return outputs.get(id);
    }

    public Map<StageId, StageOutput> allOutputs() {
        return outputs;
    }

    public void decide(DecisionRecord record) {
        lineage.add(record);
    }

    public List<DecisionRecord> lineage() {
        return List.copyOf(lineage);
    }

    public String clarificationAnswer() {
        return clarificationAnswer;
    }

    public void setClarificationAnswer(String clarificationAnswer) {
        this.clarificationAnswer = clarificationAnswer;
    }

    /** Builds a compact prior-stage-context string for prompting: what has been decided so far, in order. */
    public String priorContextSummary() {
        StringBuilder sb = new StringBuilder();
        for (StageOutput o : outputs.values()) {
            if (o.summary() == null) continue;
            sb.append("- ").append(o.stageId()).append(": ").append(o.summary()).append('\n');
        }
        if (clarificationAnswer != null) {
            sb.append("- Human clarification provided: ").append(clarificationAnswer).append('\n');
        }
        return sb.toString();
    }
}

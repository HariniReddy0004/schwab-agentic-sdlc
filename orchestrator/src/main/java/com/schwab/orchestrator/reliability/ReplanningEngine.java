package com.schwab.orchestrator.reliability;

import com.schwab.orchestrator.execution.ReplanEvent;
import com.schwab.orchestrator.execution.Run;
import com.schwab.orchestrator.graph.GraphBuilder;
import com.schwab.orchestrator.graph.StageDefinition;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.StageOutput;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Inspects a just-completed stage's output and decides whether the graph needs to change. This is
 * the "dynamically re-plan when upstream outputs change" requirement, made concrete: today the
 * only trigger implemented is REQUIREMENT_UNDERSTANDING flagging ambiguity, which splices in a
 * CLARIFICATION checkpoint and rewires TASK_DECOMPOSITION to depend on it — but the mechanism
 * (inspect StageOutput -> mutate DependencyGraph -> record ReplanEvent) generalizes to other
 * triggers (e.g. a codebase-reasoning stage discovering a dependency nobody planned for).
 */
public final class ReplanningEngine {

    /** Must be called with run.stateLock() held. Returns true if a re-plan was applied. */
    public boolean evaluateAfter(Run run, StageId completedStage, StageOutput output) {
        if (completedStage == StageId.REQUIREMENT_UNDERSTANDING && output.ambiguous()
                && !run.graph().contains(StageId.CLARIFICATION)) {

            StageDefinition clarification = GraphBuilder.clarificationStage();
            StageDefinition originalDecomp = run.graph().get(StageId.TASK_DECOMPOSITION);
            Set<StageId> newDeps = new LinkedHashSet<>(originalDecomp.dependsOn());
            newDeps.add(StageId.CLARIFICATION);
            StageDefinition rewiredDecomp = new StageDefinition(
                    StageId.TASK_DECOMPOSITION, originalDecomp.displayName(), newDeps,
                    originalDecomp.entryGate(), originalDecomp.exitGate(), originalDecomp.maxAttempts(),
                    originalDecomp.rollbackTargets());

            run.spliceStage(clarification, StageId.TASK_DECOMPOSITION, rewiredDecomp);

            String reason = "Requirement understanding flagged ambiguity: " + output.clarifyingQuestions();
            run.recordReplan(new ReplanEvent(Instant.now(), completedStage.name(), reason,
                    "Inserted CLARIFICATION stage; rewired TASK_DECOMPOSITION to depend on it"));
            return true;
        }
        return false;
    }
}

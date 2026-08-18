package com.schwab.orchestrator.graph;

import com.schwab.orchestrator.model.ScenarioType;

import java.util.List;
import java.util.Set;

/**
 * Builds the initial SDLC dependency graph for a run. The shape is deliberately the SAME
 * generic SDLC lifecycle for every scenario type — greenfield, brownfield, and ambiguous are not
 * three different hard-coded graphs (that would be "simple linear task chaining" wearing a
 * costume). What differs:
 *  - BROWNFIELD includes CODEBASE_REASONING (there is existing code to reason about).
 *  - AMBIGUOUS requirements are not known in advance; the graph starts the same as greenfield's
 *    and the ReplanningEngine inserts CLARIFICATION at runtime if the requirement-understanding
 *    agent actually flags ambiguity — which is the honest way to model "we didn't know we'd need
 *    this step until we saw the upstream output."
 */
public final class GraphBuilder {

    public DependencyGraph build(ScenarioType scenarioType) {
        DependencyGraph g = new DependencyGraph();

        g.addStage(new StageDefinition(StageId.REQUIREMENT_UNDERSTANDING, "Requirement Understanding",
                Set.of(), GateType.NONE, GateType.NONE, 2, List.of()));

        Set<StageId> decompDeps = Set.of(StageId.REQUIREMENT_UNDERSTANDING);
        g.addStage(new StageDefinition(StageId.TASK_DECOMPOSITION, "Task Decomposition",
                decompDeps, GateType.NONE, GateType.NONE, 2, List.of()));

        StageId beforeDesign = StageId.TASK_DECOMPOSITION;
        if (scenarioType == ScenarioType.BROWNFIELD) {
            g.addStage(new StageDefinition(StageId.CODEBASE_REASONING, "Codebase Reasoning",
                    Set.of(StageId.TASK_DECOMPOSITION), GateType.NONE, GateType.NONE, 2, List.of()));
            beforeDesign = StageId.CODEBASE_REASONING;
        }

        g.addStage(new StageDefinition(StageId.ARCHITECTURE_DESIGN, "Architecture & Design",
                Set.of(beforeDesign), GateType.NONE, GateType.NONE, 2, List.of()));

        // High-impact action: writing/changing code requires a human approval checkpoint before it runs.
        g.addStage(new StageDefinition(StageId.IMPLEMENTATION, "Implementation",
                Set.of(StageId.ARCHITECTURE_DESIGN), GateType.HUMAN_APPROVAL, GateType.NONE, 3, List.of()));

        // Real fork/join: TESTING and DOCUMENTATION both depend only on IMPLEMENTATION and run in parallel;
        // RELEASE_READINESS joins on both (ALL policy) before the run can complete.
        g.addStage(new StageDefinition(StageId.TESTING, "Testing",
                Set.of(StageId.IMPLEMENTATION), GateType.NONE, GateType.NONE, 3, List.of(StageId.IMPLEMENTATION)));
        g.addStage(new StageDefinition(StageId.DOCUMENTATION, "Documentation",
                Set.of(StageId.IMPLEMENTATION), GateType.NONE, GateType.NONE, 2, List.of()));

        // Release sign-off: exit gate requires human approval before the run is considered complete.
        g.addStage(new StageDefinition(StageId.RELEASE_READINESS, "Release Readiness",
                Set.of(StageId.TESTING, StageId.DOCUMENTATION), GateType.NONE, GateType.HUMAN_APPROVAL, 2, List.of()));

        return g;
    }

    /** Used by the ReplanningEngine to splice CLARIFICATION into a running graph. */
    public static StageDefinition clarificationStage() {
        return new StageDefinition(StageId.CLARIFICATION, "Clarification (human-in-the-loop)",
                Set.of(StageId.REQUIREMENT_UNDERSTANDING), GateType.HUMAN_APPROVAL, GateType.NONE, 1, List.of());
    }
}

package com.schwab.orchestrator.graph;

/**
 * The SDLC stages the orchestrator coordinates. CLARIFICATION is not present in every run's
 * initial graph — it is inserted dynamically by the {@code ReplanningEngine} when the
 * requirement-understanding stage flags ambiguity, which is how "ambiguous requirement" scenarios
 * are handled without a special-cased graph.
 */
public enum StageId {
    REQUIREMENT_UNDERSTANDING,
    CLARIFICATION,
    TASK_DECOMPOSITION,
    CODEBASE_REASONING,
    ARCHITECTURE_DESIGN,
    IMPLEMENTATION,
    TESTING,
    DOCUMENTATION,
    RELEASE_READINESS
}

package com.schwab.orchestrator.llm;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;

/** Builds the system/user prompt pair for each SDLC stage, all asking for the same strict JSON output contract. */
public final class PromptTemplates {

    public record Prompt(String system, String user) {
    }

    private static final String JSON_CONTRACT = """
            Respond with ONLY a single JSON object (no markdown fences, no commentary before or after) matching exactly this shape:
            {
              "summary": "one paragraph summary of what this stage produced",
              "artifacts": {"artifactName": "artifact content or description"},
              "decisions": [{"description": "a decision you made", "rationale": "why"}],
              "riskFlags": ["short risk or trade-off descriptions"],
              "requiresHumanReview": true or false,
              "ambiguous": true or false,
              "clarifyingQuestions": ["question to ask a human, only if ambiguous is true, else empty array"],
              "confidence": a number between 0.0 and 1.0
            }""";

    public Prompt forStage(StageId stage, ExecutionContext ctx, RunRequest req) {
        String system = "You are an expert software engineering agent acting as the '" + displayName(stage) +
                "' stage of a governed, human-supervised agentic SDLC orchestrator building a production URL " +
                "shortener service. You operate under controlled autonomy: humans approve high-impact actions " +
                "and make final quality calls. Be concrete and specific to the URL shortener domain. " + JSON_CONTRACT;

        StringBuilder user = new StringBuilder();
        user.append("Scenario type: ").append(req.scenarioType()).append('\n');
        user.append("Requirement title: ").append(req.title()).append('\n');
        user.append("Requirement text:\n").append(req.requirementText()).append("\n\n");
        String priorContext = ctx.priorContextSummary();
        if (!priorContext.isBlank()) {
            user.append("Context already established by earlier stages in this run:\n").append(priorContext).append('\n');
        }
        user.append("Now perform the '").append(displayName(stage)).append("' stage for this requirement.\n");
        user.append(stageSpecificInstructions(stage));
        return new Prompt(system, user.toString());
    }

    private String displayName(StageId stage) {
        return switch (stage) {
            case REQUIREMENT_UNDERSTANDING -> "Requirement Understanding";
            case CLARIFICATION -> "Clarification";
            case TASK_DECOMPOSITION -> "Task Decomposition";
            case CODEBASE_REASONING -> "Codebase Reasoning";
            case ARCHITECTURE_DESIGN -> "Architecture & Design";
            case IMPLEMENTATION -> "Implementation";
            case TESTING -> "Testing";
            case DOCUMENTATION -> "Documentation";
            case RELEASE_READINESS -> "Release Readiness";
        };
    }

    private String stageSpecificInstructions(StageId stage) {
        return switch (stage) {
            case REQUIREMENT_UNDERSTANDING -> """
                    Interpret intent, restate the problem as a normalized engineering problem statement, and \
                    explicitly decide whether the requirement is well-defined enough to proceed. If it is \
                    genuinely ambiguous (missing a decision only a human/product owner can make), set \
                    ambiguous=true and list concrete clarifying questions. Do not guess at business intent.""";
            case CLARIFICATION -> """
                    Incorporate the human's clarification answer (present in the prior-stage context above) into \
                    a revised, unambiguous problem statement.""";
            case TASK_DECOMPOSITION -> """
                    Break the (possibly clarified) requirement into an ordered list of concrete engineering tasks \
                    with dependencies, sized for a single engineer-day or less each. Put the task list in the \
                    "taskList" artifact.""";
            case CODEBASE_REASONING -> """
                    Reason about which existing modules/services/APIs/data flows in the url-shortener codebase are \
                    impacted by this change, and why. Reference specific classes/packages where possible.""";
            case ARCHITECTURE_DESIGN -> """
                    Propose the concrete design: API/schema changes, affected classes, data model changes, and key \
                    trade-offs considered (and rejected alternatives). Put the design in the "designDoc" artifact.""";
            case IMPLEMENTATION -> """
                    Produce a reviewable file-level change set: exact classes/methods touched, concrete logic, \
                    validation commands, acceptance criteria, and rollback instructions. Put it in the \
                    "reviewableChangeSet" artifact. Do not claim a repository mutation unless tool evidence is present.""";
            case TESTING -> """
                    Define the test plan: unit tests, integration tests, edge cases, and failure scenarios. Put a \
                    concrete list of test cases in the "testPlan" artifact.""";
            case DOCUMENTATION -> """
                    Produce user-facing and developer-facing documentation for this change: what changed, how to \
                    use it, and any migration notes. Put it in the "documentation" artifact.""";
            case RELEASE_READINESS -> """
                    Assess release readiness: outstanding risks, rollback plan if the release misbehaves, and a \
                    go/no-go recommendation with rationale. Put the recommendation in the "releaseAssessment" artifact.""";
        };
    }
}

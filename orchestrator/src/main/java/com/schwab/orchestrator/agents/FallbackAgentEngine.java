package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.execution.ExecutionContext;
import com.schwab.orchestrator.graph.StageId;
import com.schwab.orchestrator.model.RunRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic, rule-based stand-in for the LLM, used whenever ANTHROPIC_API_KEY is not
 * configured (or a live call fails after retries are exhausted). This keeps the system genuinely
 * "runnable end-to-end" without external credentials while still exercising every governance,
 * retry, gate, and reliability code path — only the content-generation step differs from a live
 * LLM call. It is not trying to out-write the model; it is trying to be honest, structured, and
 * traceable about which requirement keywords drove which output.
 */
public final class FallbackAgentEngine {

    private static final List<String> VAGUENESS_MARKERS = List.of(
            "better", "faster", "improve", "some kind of", "etc", "as needed", "maybe", "somehow",
            "modernize", "optimize it", "make it nice"
    );

    public StageAgentResult generate(StageId stage, ExecutionContext ctx, RunRequest req) {
        return switch (stage) {
            case REQUIREMENT_UNDERSTANDING -> requirementUnderstanding(req);
            case CLARIFICATION -> clarification(ctx, req);
            case TASK_DECOMPOSITION -> taskDecomposition(req);
            case CODEBASE_REASONING -> codebaseReasoningFallback(req);
            case ARCHITECTURE_DESIGN -> architectureDesign(req);
            case IMPLEMENTATION -> implementation(req);
            case TESTING -> testing(req);
            case DOCUMENTATION -> documentation(req);
            case RELEASE_READINESS -> releaseReadiness(ctx);
        };
    }

    private StageAgentResult requirementUnderstanding(RunRequest req) {
        String text = req.requirementText() == null ? "" : req.requirementText();
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> matchedMarkers = VAGUENESS_MARKERS.stream().filter(lower::contains).toList();
        boolean tooShort = text.split("\\s+").length < 12;
        boolean ambiguous = !matchedMarkers.isEmpty() || tooShort;

        List<String> questions = new ArrayList<>();
        if (!matchedMarkers.isEmpty()) {
            questions.add("The requirement uses vague terms (" + String.join(", ", matchedMarkers) +
                    "). What specific, measurable outcome defines success?");
        }
        if (tooShort) {
            questions.add("The requirement is very brief. Which specific API(s), data fields, or user flows should change?");
        }
        questions.add("What is the priority relative to existing work: is this a hard blocker for release, or best-effort?");

        String summary = ambiguous
                ? "Normalized problem statement drafted, but the requirement leaves at least one product/engineering " +
                  "decision unresolved (" + String.join("; ", matchedMarkers.isEmpty() ? List.of("insufficient detail") : matchedMarkers) + "). Flagging for human clarification before decomposition."
                : "Normalized problem statement: " + text.strip() + ". No blocking ambiguity detected; proceeding to decomposition.";

        return new StageAgentResult(
                summary,
                StageAgentResult.singleArtifact("problemStatement", "Scenario=" + req.scenarioType() + "; Normalized requirement: " + text.strip()),
                List.of(new StageAgentResult.Decision(
                        ambiguous ? "Flag requirement as ambiguous rather than assume intent" : "Accept requirement as well-defined",
                        ambiguous ? "Requirement contains vague/underspecified language; guessing risks building the wrong thing" :
                                "Requirement is specific enough (length + no vagueness markers) to decompose safely")),
                ambiguous ? List.of("Ambiguous requirement: proceeding without clarification risks rework") : List.of(),
                ambiguous,
                ambiguous,
                ambiguous ? questions : List.of(),
                ambiguous ? 0.4 : 0.85
        );
    }

    private StageAgentResult clarification(ExecutionContext ctx, RunRequest req) {
        String answer = ctx.clarificationAnswer();
        String summary = answer == null
                ? "No clarification answer was recorded; proceeding with the original requirement text as a best-effort interpretation."
                : "Incorporated human clarification: " + answer;
        return new StageAgentResult(
                summary,
                StageAgentResult.singleArtifact("revisedProblemStatement", req.requirementText().strip() +
                        (answer != null ? " | Clarification: " + answer : "")),
                List.of(new StageAgentResult.Decision("Incorporate human answer into problem statement",
                        "A human approval checkpoint supplied the missing decision; the agent should not re-guess it")),
                List.of(), false, false, List.of(), 0.9
        );
    }

    private StageAgentResult taskDecomposition(RunRequest req) {
        List<String> tasks = new ArrayList<>(List.of(
                "1. Define/validate request & response schema for the change",
                "2. Implement service-layer logic",
                "3. Wire persistence/repository changes if data model is affected",
                "4. Add/extend REST endpoint(s) and input validation",
                "5. Add unit tests for service logic",
                "6. Add integration tests exercising the HTTP API end-to-end",
                "7. Update documentation (README/API docs)",
                "8. Assess release readiness and rollback plan"
        ));
        if (req.scenarioType() == com.schwab.orchestrator.model.ScenarioType.BROWNFIELD) {
            tasks.add(2, "2a. Identify impacted existing modules/classes before making changes (codebase reasoning)");
        }
        return new StageAgentResult(
                "Decomposed the requirement into " + tasks.size() + " sequenced engineering tasks.",
                StageAgentResult.singleArtifact("taskList", String.join("\n", tasks)),
                List.of(new StageAgentResult.Decision("Sequence tests before documentation and release assessment",
                        "Docs and go/no-go calls should reflect what was actually verified, not what was intended")),
                List.of(), false, false, List.of(), 0.8
        );
    }

    private StageAgentResult codebaseReasoningFallback(RunRequest req) {
        // Real static-analysis reasoning lives in CodebaseReasoningAgent; this is only reached if that
        // agent itself needs a textual fallback summary when no repo path was supplied.
        return new StageAgentResult(
                "No repository context path was supplied; skipped static impact analysis and relied on the requirement text alone.",
                Map.of(),
                List.of(),
                List.of("Codebase reasoning ran without repository access; impacted-module list may be incomplete"),
                true, false, List.of(), 0.3
        );
    }

    private StageAgentResult architectureDesign(RunRequest req) {
        String design = "Design for: " + req.title() + "\n" +
                "- API surface: extend existing url-shortener REST controllers (ShortenController/AnalyticsController) " +
                "or add a new controller, following the existing Router/HttpCtx pattern.\n" +
                "- Data model: extend ShortUrl/ClickEvent records or add a new model class as needed.\n" +
                "- Persistence: extend the repository interface first, then the in-memory implementation, " +
                "so a future durable-store implementation is a drop-in replacement.\n" +
                "- Rejected alternative: embedding this logic directly in the controller was rejected to keep " +
                "business logic testable independent of HTTP.";
        return new StageAgentResult(
                "Produced a design consistent with the existing layered architecture (controller/service/repository).",
                StageAgentResult.singleArtifact("designDoc", design),
                List.of(new StageAgentResult.Decision("Extend existing layers rather than introduce a parallel structure",
                        "Consistency with the established architecture reduces cognitive load and review risk")),
                List.of(), false, false, List.of(), 0.75
        );
    }

    private StageAgentResult implementation(RunRequest req) {
        String lower = (req.title() + " " + req.requirementText()).toLowerCase(Locale.ROOT);
        String affectedFiles;
        String concreteChanges;
        if (lower.contains("rate limit") && lower.contains("redirect")) {
            affectedFiles = "RedirectController.java, UrlShortenerApp.java, HttpApiIntegrationTest.java";
            concreteChanges = "Inject a dedicated RateLimiterService into RedirectController; check the client address " +
                    "before resolving the code; return 429 on exhaustion; wire a separately configured redirect limiter; " +
                    "add an HTTP integration test for the allowed and exhausted paths.";
        } else if (lower.contains("analytic")) {
            affectedFiles = "AnalyticsService.java, AnalyticsController.java, HttpApiIntegrationTest.java";
            concreteChanges = "Aggregate click events by UTC day of week; rank referrers by click count with deterministic " +
                    "tie-breaking; return only the top five; preserve existing analytics fields; add integration assertions.";
        } else if (lower.contains("ttl") || lower.contains("expir")) {
            affectedFiles = "UrlShortenerService.java, ShortUrl.java, UrlShortenerServiceTest.java";
            concreteChanges = "Validate positive bounded ttlSeconds; calculate expiresAt from the injected Clock; return 410 " +
                    "at and after the expiration boundary; cover zero, negative, excessive, and exact-boundary values.";
        } else {
            affectedFiles = "Controller, service, repository (if persistence changes), and matching unit/integration tests";
            concreteChanges = "Add the smallest change behind the existing interfaces; validate inputs at the API boundary; " +
                    "keep business logic in the service; preserve existing behavior through regression tests.";
        }
        String changeSet = "Change: " + req.title() + "\n" +
                "Affected files: " + affectedFiles + "\n" +
                "Implementation: " + concreteChanges + "\n" +
                "Validation: run ./url-shortener/test.sh and verify HTTP status/error contracts.\n" +
                "Rollback: revert the isolated controller/service wiring change and redeploy the last verified build.";
        return new StageAgentResult(
                "Produced a reviewable, file-level change set with validation and rollback instructions.",
                StageAgentResult.singleArtifact("reviewableChangeSet", changeSet),
                List.of(new StageAgentResult.Decision("Keep controller thin, push logic to service layer",
                        "Matches existing convention; keeps controller unit-testable without an HTTP server")),
                List.of("Implementation stage is a high-impact action and required human approval before running"),
                true, false, List.of(), 0.7
        );
    }

    private StageAgentResult testing(RunRequest req) {
        String plan = "Test plan for: " + req.title() + "\n" +
                "- Unit: happy path, invalid input, boundary values\n" +
                "- Unit: idempotency / repeated-call behavior where relevant\n" +
                "- Integration: full HTTP round-trip via the real router + in-memory repository\n" +
                "- Failure scenario: dependency unavailable / repository conflict\n" +
                "- Regression: existing endpoints unaffected by the change";
        return new StageAgentResult(
                "Defined a test plan covering unit, integration, and failure-scenario coverage.",
                StageAgentResult.singleArtifact("testPlan", plan),
                List.of(), List.of(), false, false, List.of(), 0.8
        );
    }

    private StageAgentResult documentation(RunRequest req) {
        String docs = "## " + req.title() + "\n\nWhat changed, why, and how to use it, written for both API consumers " +
                "and future maintainers. Includes migration notes if any existing behavior changed.";
        return new StageAgentResult(
                "Drafted user-facing and developer-facing documentation for the change.",
                StageAgentResult.singleArtifact("documentation", docs),
                List.of(), List.of(), false, false, List.of(), 0.8
        );
    }

    private StageAgentResult releaseReadiness(ExecutionContext ctx) {
        boolean testingOk = ctx.outputOf(StageId.TESTING) != null && !ctx.outputOf(StageId.TESTING).artifacts().isEmpty();
        boolean docsOk = ctx.outputOf(StageId.DOCUMENTATION) != null && !ctx.outputOf(StageId.DOCUMENTATION).artifacts().isEmpty();
        boolean goodToGo = testingOk && docsOk;
        String assessment = "Go/No-Go: " + (goodToGo ? "GO" : "NO-GO") +
                ". Testing artifacts present: " + testingOk + ". Documentation artifacts present: " + docsOk +
                ". Rollback plan: deactivate the new short codes/feature flag and redeploy the prior build.";
        return new StageAgentResult(
                "Assessed release readiness based on upstream stage outputs.",
                StageAgentResult.singleArtifact("releaseAssessment", assessment),
                List.of(new StageAgentResult.Decision(goodToGo ? "Recommend release" : "Recommend blocking release",
                        goodToGo ? "Both test and documentation evidence are present" : "Missing test or documentation evidence")),
                goodToGo ? List.of() : List.of("Release recommended against due to missing test/documentation evidence"),
                true, false, List.of(), goodToGo ? 0.8 : 0.3
        );
    }
}

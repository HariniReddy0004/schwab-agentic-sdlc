# Scenario: Brownfield — Rate limit the redirect endpoint

## Requirement (as submitted)

> The redirect endpoint currently has no rate limiting, unlike URL creation which already uses
> RateLimiterService. Add per-IP rate limiting to the redirect path to prevent scraping/abuse,
> reusing the existing RateLimiterService pattern instead of introducing a new mechanism, and
> return 429 when the limit is exceeded.

This is an enhancement to the existing `url-shortener` service, so the run was submitted with
`repoContextPath` pointing at that project's source tree — this is what makes CODEBASE_REASONING a
real static-analysis step instead of a guess.

## What happened, end to end

Run id: `run-0896fbd7` (raw evidence in `01_*.json` .. `08_graph.json`).

1. **Requirement Understanding** — not ambiguous (specific about mechanism reuse and the exact
   status code).
2. **Task Decomposition** — included an extra brownfield-specific first task ("identify impacted
   existing modules/classes before making changes") ahead of the generic task list.
3. **Codebase Reasoning** — this is the interesting stage for this scenario. `CodebaseReasoningAgent`
   is **not** an LLM call: it extracts keywords from the requirement text (`redirect`, `rate`,
   `limiting`, `ratelimiterservice`, `path`, `exceeded`, ...), walks every `.java` file under the
   supplied repo path, and ranks files by keyword-match count. The actual result (see
   `05_stage_outputs.json` → `CODEBASE_REASONING`):
   - `RateLimiterService.java` and `ShortenController.java` (which already uses the rate limiter for
     URL creation) both surfaced near the top of the ranked list, genuinely correct — those are
     exactly the classes an engineer would open first for this change.
   - `HttpCtx.java` and `Router.java` also surfaced (they carry `path`/`redirect` semantics), which
     is a reasonable, defensible false-positive-leaning result for a keyword heuristic — a real
     engineer would glance at them and move on, which is exactly what a "candidate impacted
     modules" list is for.
4. **Architecture & Design** — depends on CODEBASE_REASONING in the brownfield graph (see
   `08_graph.json`: `ARCHITECTURE_DESIGN.dependsOn` includes `CODEBASE_REASONING`, which is not true
   in the greenfield graph).
5. **Implementation — human approval checkpoint** — paused for approval
   (`02_waiting_for_implementation_approval.json`), approved referencing the codebase-reasoning
   result specifically.
6. **Testing** / **Documentation** (parallel) → **Release Readiness — human approval checkpoint** →
   `COMPLETED` (`04_final_status.json`).

## Codebase reasoning demonstrated

`05_stage_outputs.json` → `CODEBASE_REASONING.artifacts.impactedModules` is the literal ranked
output — included verbatim in this scenario's evidence rather than summarized, since "identify
impacted modules/services/APIs/data flows and demonstrate architectural understanding" is the
specific brownfield requirement being tested here.

## Orchestration points demonstrated

- The dependency graph is genuinely different per scenario type (an extra node, extra edge) — not
  just a different label on the same fixed chain. Compare `08_graph.json` here against
  `../greenfield/09_graph.json`.
- The same governance/reliability machinery (guardrails, gates, retries, audit) that ran in the
  greenfield scenario ran here unmodified — brownfield-specific behavior lives entirely in the
  graph shape and the CODEBASE_REASONING agent, not in a parallel orchestrator code path.

## Validation

- The codebase-reasoning output is auditable and falsifiable: a reviewer can independently grep the
  same repo for the same keywords and check the ranking, rather than trusting an LLM's unverifiable
  claim about "impacted modules."

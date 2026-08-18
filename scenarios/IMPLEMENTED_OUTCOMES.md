# Implemented Scenario Outcomes

The JSON files in each scenario folder are immutable execution evidence: they show what the
orchestrator knew and produced at that time. This page connects those decisions to the final target
application without rewriting historical run data.

| Scenario | Reviewable outcome in the run | Implemented and validated in the target |
| --- | --- | --- |
| Greenfield | A governed new-feature workflow using a QR endpoint proposal | The URL shortener itself is the greenfield system built for the assignment. Its implemented capabilities include `ttlSeconds`, bounded expiration, exact-boundary `410 Gone`, analytics, reliability controls, and HTTP integration coverage. The QR proposal remains a reviewable design artifact rather than a falsely claimed repository mutation. |
| Brownfield | Reuse `RateLimiterService` on the existing redirect path | `RedirectController` now has a dedicated per-client limiter, `UrlShortenerApp` wires it independently from creation limits, and `HttpApiIntegrationTest.redirectRateLimitReturns429WhenExhausted` verifies `302` followed by `429`. |
| Ambiguous | Clarified analytics scope: UTC day-of-week counts and top-five referrers | `AnalyticsService` now returns `clicksByDayOfWeekUtc` and deterministically ranked `topReferrers`; `AnalyticsServiceTest` and the HTTP integration test verify the contract. |

This separation is intentional. Audit evidence should not be edited after a run. The current source
and tests show the final engineering outcome, while the scenario evidence shows how the decisions
were reached.

# Assignment Requirements Traceability

This page is a reviewer map: it shows where each assignment expectation is implemented and how it
can be demonstrated. The design intentionally separates the engineering target (`url-shortener`)
from the agentic control plane (`orchestrator`) so each can be tested and reasoned about independently.

| Assignment expectation | Implementation evidence |
| --- | --- |
| Requirement understanding | `REQUIREMENT_UNDERSTANDING` stage normalizes intent and records decisions in the shared execution context. |
| Task decomposition | `TASK_DECOMPOSITION` produces actionable work while `DependencyGraph` defines ordering and dependencies. |
| Brownfield codebase reasoning | `CodebaseReasoningAgent` inspects the supplied repository context; the brownfield scenario records impacted modules and APIs. |
| Non-linear orchestration | `ExecutionEngine` schedules dependency-ready nodes; testing and documentation run concurrently and synchronize before release readiness. |
| Stateful context and lineage | `Run`, `ExecutionContext`, `StageOutput`, and `DecisionRecord` preserve state, artifacts, decisions, and rationale across stages. |
| Entry and exit gates | Implementation uses an entry approval; release readiness uses an exit approval; clarification also requires human input. |
| Bounded retries and fallback | Each stage has a maximum attempt count and exponential delay before the deterministic fallback engine is used. |
| Rollback and safe stop | `RollbackRegistry` compensates completed work after permanent failure; operators can request a terminal safe stop. |
| Security, compliance, change control | `GovernanceEngine` evaluates three independent guardrails before a stage is scheduled. |
| Audit-grade traceability | `AuditLogger` records run, stage, actor, event, timestamp, message, and structured data in memory and JSON Lines. |
| Reliability metrics | `MetricsRegistry` reports run success/failure, retries, fallback use, rollbacks, approvals, guardrail blocks, recovery time, and end-to-end latency. |
| Dynamic replanning | `ReplanningEngine` inserts a clarification node and rewires dependencies when upstream output is ambiguous. |
| Working engineering output | `url-shortener` provides create, redirect, metadata, analytics, deactivation, expiration, collision handling, and rate limiting. |
| API and schema definitions | `docs/API_CONTRACT.md` explains the contract and `docs/openapi.yaml` provides a machine-readable OpenAPI 3.1 definition. |
| Unit and integration validation | The repository contains 48 tests across domain logic, HTTP APIs, graph behavior, governance, metrics, and end-to-end orchestration. |
| Three required scenarios | `scenarios/greenfield`, `scenarios/brownfield`, and `scenarios/ambiguous` contain explanations and captured API evidence. |
| Scenario-to-code follow-through | `scenarios/IMPLEMENTED_OUTCOMES.md` maps each decision to final source files and automated tests without altering historical run evidence. |
| Setup, limitations, trade-offs | `docs/SETUP.md`, `docs/TESTING_AND_TRADEOFFS.md`, and `docs/FINAL_ENGINEERING_SUMMARY.md`. |

## Recommended review path

1. Read the root `README.md` and `docs/ARCHITECTURE.md`.
2. Run both test suites.
3. Start both services and execute the greenfield scenario.
4. Inspect the graph, outputs, decision lineage, audit trail, and metrics endpoints.
5. Review the brownfield and ambiguous evidence to see codebase reasoning and dynamic replanning.

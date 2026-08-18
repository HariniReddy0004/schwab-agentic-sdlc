# Recruiter Walkthrough

This is the shortest path for reviewing the assignment. It focuses on the behavior that makes the
solution agentic instead of merely being a URL shortener with a sequence of scripted steps.

## My two-minute explanation

I built two small Java 21 applications. The URL shortener is the working product: it creates short
links, redirects users, expires or deactivates links, records clicks, and exposes analytics. The
second application is the main part of the assignment. It turns an engineering requirement into a
stateful SDLC workflow.

The workflow is represented as a dependency graph. A stage runs only when its dependencies and
entry conditions are satisfied. Testing and documentation can run in parallel, but release
readiness waits for both. Implementation and release also have human approval gates.

I kept gates and guardrails separate. Approval means a person accepted the action; a guardrail
still checks whether it violates security, compliance, or change-control policy. Failures use
bounded retries, then a deterministic fallback. If a completed action needs compensation, the
engine invokes its rollback action. A safe-stop endpoint lets a human halt the run.

The most interesting scenario starts with the vague request, "Make analytics better." The engine
does not guess. It recognizes the ambiguity, inserts a clarification stage into the graph, pauses
for a human answer, and continues with that answer in the shared context. The graph, approvals,
outputs, decisions, retries, and timings remain visible through the audit and lineage APIs.

## How I used AI-assisted development

I used Claude, ChatGPT, and Gemini as supporting tools while working on the assessment. They were
useful for clarifying requirements, comparing implementation ideas, checking possible edge cases,
and improving documentation. I reviewed the suggestions I used and tested the final solution
myself. This is similar to the approach demonstrated by the orchestrator: AI can assist the work,
but important actions still need review, guardrails, and validation.

## Five-minute review path

1. Run all tests from the repository root:

   ```bash
   ./test-all.sh
   ```

2. Read the ambiguous scenario:

   - `scenarios/ambiguous/README.md`
   - `scenarios/ambiguous/09_graph.json`
   - `scenarios/ambiguous/07_decision_lineage.json`

3. See the implementation behind it:

   - `orchestrator/src/main/java/com/schwab/orchestrator/graph/DependencyGraph.java`
   - `orchestrator/src/main/java/com/schwab/orchestrator/execution/ExecutionEngine.java`
   - `orchestrator/src/main/java/com/schwab/orchestrator/reliability/ReplanningEngine.java`
   - `orchestrator/src/main/java/com/schwab/orchestrator/governance/GovernanceEngine.java`

4. Use `docs/REQUIREMENTS_TRACEABILITY.md` to map every assessment requirement to evidence.

## What makes this solution different

- The graph can change while a run is active; ambiguity is not handled by a prewritten alternate
  script.
- Parallel stages synchronize before release readiness.
- Human approval cannot bypass independent policy guardrails.
- Every important action creates reviewable audit and decision-lineage evidence.
- The solution runs without an API key, while retaining an optional Claude adapter.
- The target application and orchestration engine have 48 focused tests, including HTTP-level
  integration tests and failure-path tests.

## Operational quick reference

- **Guardrail enforcement:** `SecurityGuardrail`, `ComplianceGuardrail`, and
  `ChangeControlGuardrail` evaluate work independently from human approval. The appendix scenario
  shows a security-sensitive change being blocked even after a person approved it.
- **Reliability tracking:** `MetricsRegistry` records success rate, retries, fallback use,
  rollback frequency, recovery time, end-to-end latency, approval decisions, and guardrail blocks.
- **Safe operation:** operators can stop a non-terminal run through the safe-stop API. Permanent
  stage failures trigger registered compensating rollback actions before finalization.
- **Zero external runtime dependencies:** both services use Java 21's native HTTP server, HTTP
  client, virtual threads, and standard library. No Spring, Jackson, database, or application
  server is required for the assessment prototype.

The captured four-run metrics snapshot provides concrete evidence:

| Metric | Captured value |
| --- | ---: |
| Successful runs | 3 of 4 |
| Guardrail blocks | 1 |
| Approvals granted | 8 |
| Retry frequency | 0.536 retries per recorded stage execution |
| Rollbacks | 0 |
| Mean recovery time | 219.48 ms |
| Average end-to-end latency | 16,738.75 ms |

The zero rollback count is expected in this sample: the fourth run was stopped by a guardrail
before a compensating action became necessary. The rollback registry and failure-containment path
are implemented, while a forced rollback demonstration is identified as additional chaos-testing
work rather than presented as evidence that did not occur.

## Honest limitations

This is an assessment prototype, so state and rate limits are held in memory. A production version
would add authentication, persistent storage, distributed coordination, secrets management,
tracing, and load testing. These are documented trade-offs, not hidden assumptions.

## Questions I am ready to answer

**Why use a dependency graph?**  
It makes prerequisites, parallel work, joins, and replanning explicit and testable.

**Why keep approvals and guardrails separate?**  
A person can approve something unsafe by mistake. Policy checks provide a second control.

**Why include deterministic fallback?**  
The workflow remains demonstrable without exposing an API key, and model failure does not make the
entire system unusable.

**Why Java 21?**  
Java 21 is an LTS release with broad enterprise adoption and supports the standard-library features
used here, including virtual threads. Newer JDKs can still build the project because compilation
targets Java 21 bytecode.

**What would I improve next?**  
I would persist workflow state, add authenticated role-based approvals, use a durable event queue,
and replace heuristic code analysis with AST and SAST integrations.

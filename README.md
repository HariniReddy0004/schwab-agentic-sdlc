# Schwab Agentic SDLC Framework

[![Java 21 verification](https://github.com/HariniReddy0004/schwab-agentic-sdlc/actions/workflows/ci.yml/badge.svg)](https://github.com/HariniReddy0004/schwab-agentic-sdlc/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
![Tests](https://img.shields.io/badge/Tests-48%20focused-brightgreen.svg)
![Build](https://img.shields.io/badge/Build-Zero%20Dependency-blue.svg)

A working prototype of an agentic SDLC orchestrator, built against a URL shortener service as the
target artifact. Two independent Java 21 applications:

- **`orchestrator/`** — the actual deliverable. A governed, stateful, LLM-driven orchestration
  engine that walks a requirement through requirements → decomposition → (codebase reasoning) →
  design → implementation → testing → documentation → release readiness, with human approval
  gates, policy guardrails, bounded retries, fallback, rollback, dynamic re-planning, and
  audit-grade observability.
- **`url-shortener/`** — the target system being built/enhanced. A REST URL shortener with create,
  redirect, analytics, and reliability features. It exists so the orchestrator has something real
  to reason about and produce output for (see the brownfield scenario, which does genuine static
  analysis against this codebase).

## Start here

- **Quick start:** run [`./test-all.sh`](test-all.sh) from the repository root to compile both applications and execute all 48 tests.
- **Recruiters:** [`docs/RECRUITER_WALKTHROUGH.md`](docs/RECRUITER_WALKTHROUGH.md) — five-minute review and two-minute project explanation.
- **Architecture and AI practices:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/TESTING_AND_TRADEOFFS.md`](docs/TESTING_AND_TRADEOFFS.md) — orchestration design, AI-assisted execution, testing, limitations, and trade-offs.
- **Developers:** [`docs/SETUP.md`](docs/SETUP.md), [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md), and [`docs/openapi.yaml`](docs/openapi.yaml) — build instructions and API contract.
- **Technical reviewers:** [`docs/REQUIREMENTS_TRACEABILITY.md`](docs/REQUIREMENTS_TRACEABILITY.md) and [`docs/FINAL_ENGINEERING_SUMMARY.md`](docs/FINAL_ENGINEERING_SUMMARY.md) — requirement evidence, decisions, risks, assumptions, and validation.
- **Execution evidence:** [`scenarios/`](scenarios/) — captured greenfield, brownfield, ambiguous, and guardrail-block runs.

## Build approach

Both services target **JDK 21** and have no third-party dependencies. They use the JDK's built-in
HTTP server/client plus small, purpose-built routing, JSON, and testing utilities. This keeps the
prototype easy to run and review while preserving controller, service, repository, governance, and
execution boundaries that can later be moved behind a production framework.

The repository includes 25 tests for `url-shortener`, 23 for `orchestrator`, and three captured
end-to-end scenario runs under `scenarios/`. Run both test scripts with JDK 21 or newer before submission;
they compile the applications from a clean output directory before executing the suite.

## AI-assisted development approach

I built this project with assistance from Claude, ChatGPT, and Gemini, as encouraged by the
assessment. I mainly used them to clarify requirements, compare a few implementation ideas, think
through edge cases, and improve parts of the documentation. I reviewed the suggestions before
using them and verified the final project by running all 48 tests.

## Quick start

First, verify everything with one command:

```bash
./test-all.sh
```

```bash
# terminal 1
cd url-shortener && ./run.sh          # listens on :8081

# terminal 2
cd orchestrator && ./run.sh           # listens on :8080

# terminal 3
curl -X POST http://localhost:8080/api/v1/runs \
  -H "Content-Type: application/json" \
  -d '{"scenarioType":"GREENFIELD","title":"Add /version endpoint","requirementText":"Add a GET /version endpoint returning the running build git SHA."}'
```

See `docs/SETUP.md` for the full walkthrough including how to drive a run through its approval
gates, and how to point the orchestrator's `ANTHROPIC_API_KEY` at a real Claude API key so the
LLM-backed stages make live model calls instead of using the deterministic fallback.

## Final pre-push check

```bash
chmod +x verify-java21.sh test-all.sh url-shortener/*.sh orchestrator/*.sh
./verify-java21.sh
./test-all.sh
```

Push only after the command reports all 48 tests passing. After creating the GitHub repository,
replace the static CI badge at the top with the repository-specific Actions badge URL if live
branch status is desired.

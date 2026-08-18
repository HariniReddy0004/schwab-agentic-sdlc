# Setup

## Requirements

- JDK 21 or newer (uses `java.net.http`, virtual threads, records, switch expressions, and
  `com.sun.net.httpserver` — all standard JDK, no modules need enabling).
- No Maven, no Gradle, no internet access required to build. Both projects build with `javac`
  directly (see `docs/TESTING_AND_TRADEOFFS.md` for why — short version: this sandbox cannot reach
  Maven Central).

Check your JDK:

```bash
java -version   # expect 21 or newer
```

## Build & run the url-shortener

```bash
cd url-shortener
./build.sh      # javac -d target/classes ...
./run.sh        # builds, then starts on :8081 (override with PORT=xxxx)
```

Try it:

```bash
curl -X POST http://localhost:8081/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://www.schwab.com/research","customAlias":"research1"}'

curl -i http://localhost:8081/research1                       # 302 redirect
curl http://localhost:8081/api/v1/urls/research1/analytics    # click stats
curl http://localhost:8081/health
```

## Build & run the orchestrator

```bash
cd orchestrator
./build.sh
ANTHROPIC_API_KEY=your-key  ./run.sh    # key is OPTIONAL — see below. Starts on :8080 (override with PORT=xxxx)
```

If `ANTHROPIC_API_KEY` is not set, every LLM-backed stage automatically uses the deterministic
`FallbackAgentEngine` instead of a live model call — the orchestrator is fully runnable end-to-end
either way. Startup logs which mode is active:

```
orchestrator listening on port 8080
ANTHROPIC_API_KEY configured: false (when false, all LLM-backed stages use the deterministic fallback agent)
```

## Driving a run through the API

**1. Start a run.** `scenarioType` is one of `GREENFIELD`, `BROWNFIELD`, `AMBIGUOUS`.
`repoContextPath` is only used by brownfield's codebase-reasoning stage — point it at the
`url-shortener` checkout to see real static analysis against this repo:

```bash
curl -X POST http://localhost:8080/api/v1/runs \
  -H "Content-Type: application/json" \
  -d '{
        "scenarioType": "BROWNFIELD",
        "title": "Rate limit the redirect endpoint",
        "requirementText": "Add per-IP rate limiting to the redirect path, reusing RateLimiterService.",
        "repoContextPath": "/absolute/path/to/url-shortener",
        "requestedBy": "you"
      }'
# => {"runId":"run-xxxxxxxx", "state":"RUNNING", ...}
```

**2. Watch it progress / find pending approvals:**

```bash
curl http://localhost:8080/api/v1/runs/run-xxxxxxxx | python3 -m json.tool
```

When `pendingApprovals` is non-empty, the run is paused at a gate — `state` will read
`WAITING_APPROVAL`.

**3. Approve (or reject) a checkpoint.** The checkpoint id is `{STAGE}:{ENTRY|EXIT}` (e.g.
`IMPLEMENTATION:ENTRY`, `RELEASE_READINESS:EXIT`, or `CLARIFICATION:ENTRY` for ambiguous runs). For
a `CLARIFICATION` checkpoint, put your actual clarifying answer in `comment` — it becomes the run's
`clarificationAnswer` and is fed back into every later stage's context:

```bash
curl -X POST http://localhost:8080/api/v1/runs/run-xxxxxxxx/approvals/IMPLEMENTATION:ENTRY \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVED","approver":"you","comment":"looks good"}'
```

A run typically needs two approvals to complete: `IMPLEMENTATION:ENTRY` and
`RELEASE_READINESS:EXIT` (three for an ambiguous run: `CLARIFICATION:ENTRY` first).

**4. Inspect the evidence trail:**

```bash
curl http://localhost:8080/api/v1/runs/run-xxxxxxxx/outputs    # what each stage actually produced
curl http://localhost:8080/api/v1/runs/run-xxxxxxxx/lineage    # who/what decided what, and why
curl http://localhost:8080/api/v1/runs/run-xxxxxxxx/audit      # every attempt/retry/gate/guardrail event
curl http://localhost:8080/api/v1/runs/run-xxxxxxxx/graph      # the (possibly re-planned) DAG
curl http://localhost:8080/api/v1/metrics                      # success rate, retry/rollback freq, MTTR, latency
```

**5. Safe-stop a run at any time:**

```bash
curl -X POST http://localhost:8080/api/v1/runs/run-xxxxxxxx/safe-stop \
  -H "Content-Type: application/json" -d '{"reason":"pausing for maintenance"}'
```

`scenarios/*/README.md` contains three complete worked examples (including every raw HTTP
request/response) if you'd rather read than replay.

## Running the test suites

```bash
./test-all.sh                      # run all 48 tests from the repository root

# Or run each suite separately:
cd url-shortener && ./test.sh      # 25 tests: unit + full HTTP integration coverage
cd orchestrator  && ./test.sh      # 23 tests: graph, guardrails, metrics, full engine runs, HTTP API
```

Both scripts compile from scratch and exit non-zero on any failure, so they're CI-ready as-is
(`./build.sh && ./test.sh` in each module is a complete pipeline).

## Environment variables (orchestrator)

| Variable              | Default              | Purpose                                                             |
|------------------------|----------------------|-----------------------------------------------------------------------|
| `PORT`                 | `8080`                | HTTP port                                                              |
| `ANTHROPIC_API_KEY`    | (unset)               | If set, LLM-backed stages call the real Anthropic Messages API        |
| `ANTHROPIC_BASE_URL`   | `https://api.anthropic.com` | Override for testing against a proxy/mock                       |
| `ANTHROPIC_MODEL`      | `claude-sonnet-4-5-20250929` | Model id used for live calls                                    |
| `AUDIT_LOG_DIR`        | `audit-log`           | Where per-run JSONL audit logs are written                            |

## Environment variables (url-shortener)

| Variable          | Default                          | Purpose                                   |
|--------------------|-----------------------------------|--------------------------------------------|
| `PORT`             | `8081`                            | HTTP port                                   |
| `PUBLIC_BASE_URL`  | `http://localhost:{PORT}`         | Base URL echoed back in create responses    |

# Testing approach, limitations, and trade-offs

## Testing approach

**url-shortener — 25 tests, `url-shortener/src/test/java`:**

- `Base62EncoderTest`, `RateLimiterServiceTest`, `UrlShortenerServiceTest` — pure unit tests against
  the service layer with an in-memory repository, no HTTP involved. Cover validation rules
  (scheme/host checks, alias charset), collision/uniqueness, expiration (via an injected
  `Clock.fixed`, not a real sleep), deactivation, and rate-limit windowing (including an actual
  50ms-window expiry test with a real `Thread.sleep(80)` — short enough not to slow the suite down,
  long enough to be deterministic).
- `HttpApiIntegrationTest` — boots the *real* `HttpServer` on an ephemeral port and drives it with
  `java.net.http.HttpClient` exactly as an external client would: create → follow-redirects-off GET
  to assert the 302 `Location` header → poll the analytics endpoint (click recording is
  asynchronous, so the test polls with a short timeout rather than sleeping-and-hoping) → 404 for
  unknown codes → 400 for invalid input → 410 for a deactivated code. This is the test that would
  catch a routing regression, a serialization bug, or a wiring mistake that pure unit tests can't see.

**orchestrator — 23 tests, `orchestrator/src/test/java`:**

- `DependencyGraphTest` — topological batching produces the correct parallel groups (TESTING and
  DOCUMENTATION land in the *same* batch), brownfield vs. greenfield graph shape differs correctly,
  cycle introduction is rejected.
- `SecurityGuardrailTest`, `ComplianceGuardrailTest` — guardrail logic in isolation, no engine needed.
- `MetricsRegistryTest` — success rate / retry frequency / MTTR arithmetic.
- `ExecutionEngineTest` — boots a fully wired `ExecutionEngine` with real governance,
  reliability policies, and audit logging. The six tests cover successful completion through both
  approval gates, rejected implementation approval, security guardrail blocking after approval,
  clarification and dynamic replanning, safe stop, and rejection of blank clarification or
  duplicate terminal decisions. The suite runs without `ANTHROPIC_API_KEY`, so it also exercises
  the retry-to-fallback behavior. Asynchronous state is checked through polling helpers rather
  than fixed long sleeps.
- `OrchestratorApiIntegrationTest` — the same lifecycle, but driven entirely over real HTTP against
  a real bound `HttpServer`, including reading back `/audit`, `/lineage`, and `/metrics`.

**What's intentionally not covered**: a live call to the real Anthropic API (the automated suite
does not require secrets), true multi-process/multi-JVM concurrency (single-process only, see
below), and load/performance testing (out of scope for a 2–3 day prototype).

## Trade-offs and why

### 1. Zero third-party dependencies

The prototype uses only the Java 21 standard library. This keeps setup reproducible, avoids a
network-dependent build, and lets a reviewer compile the submission with a JDK alone. The cost is
that small HTTP, JSON, and testing utilities live in the repository rather than coming from a
framework. Their scope is intentionally narrow, and the domain and orchestration layers remain
separate from them.

Concretely, that means:
- `framework/Json.java` is a ~250-line hand-written JSON reader/writer instead of Jackson. It
  supports the full JSON grammar (objects, arrays, strings with escapes, numbers, booleans, null)
  — it just doesn't do reflection-based POJO binding, so DTOs are built/read as `Map<String,Object>`
  with small helper methods (`Json.str`, `Json.asLong`, ...) instead of annotated classes.
- `framework/Router.java` + `HttpCtx.java` + `WebServer.java` are a ~200-line hand-rolled
  replacement for Spring MVC's routing/dispatch, built on the JDK's own
  `com.sun.net.httpserver.HttpServer` (part of the standard JDK distribution, not a third-party
  jar). Path templates (`/api/v1/urls/{code}`), JSON error responses, and centralized exception
  mapping (`ApiException` → status code) all work the same way a framework's would; there's just no
  auto-configuration magic underneath.
- `testing/MicroTest.java` is a ~90-line reflection-based test runner (`@Test` annotation, assertion
  helpers, a runner that instantiates and invokes) standing in for JUnit.
- **What would change with real internet access**: swap `framework/*` for `spring-boot-starter-web`,
  swap `Json` for Jackson, swap `MicroTest` for JUnit 5 + Mockito, swap `InMemoryShortUrlRepository`
  for `spring-boot-starter-data-jpa` + a real database. The architecture was deliberately kept
  framework-shaped (controller/service/repository layering, dependency injection via constructors,
  a router-style HTTP layer) specifically so that migration would be mechanical, not a rewrite.

### 2. In-memory persistence only

`InMemoryShortUrlRepository`, `RunStore`, and `ClickEventRepository` all live in a
`ConcurrentHashMap`. State does not survive a process restart, and does not share across multiple
instances. This was a direct consequence of (1) — no JDBC driver reachable — but it's also a
reasonable prototype simplification independent of that constraint. The repository *interfaces*
(`ShortUrlRepository`) are the seam: a durable implementation is a drop-in replacement, nothing
above the repository layer would need to change. The `AuditLogger` is the one place that already
writes to disk (JSONL per run) specifically because audit trails surviving a restart matters more
than the rest of the state does for a prototype.

### 3. LLM calls default to a deterministic fallback

No `ANTHROPIC_API_KEY` is configured in this sandbox (and shouldn't be — it isn't this environment's
credential to use). `ClaudeClient` is a real, correct Anthropic Messages API client
(`java.net.http.HttpClient`, proper request/response shape, `x-api-key`/`anthropic-version`
headers) that fails *fast* with `LlmUnavailableException` when no key is present, rather than
hanging on a doomed network call. `FallbackAgentEngine` then produces a structured, deterministic
result for that stage instead. This is not a stub that returns empty strings — it does real
(if simple) work: the ambiguity detector genuinely inspects the requirement text for vagueness
markers and length, `CodebaseReasoningAgent` genuinely walks and greps the target repository (see
`scenarios/brownfield/README.md` for a concrete result), and every stage's fallback output is
shaped by the actual scenario/requirement text passed in, not a canned string.

The trade-off this creates: with a real key, `LlmBackedAgent` calls Claude with the prompts in
`llm/PromptTemplates.java` and the resulting design docs/task lists/test plans/documentation would
read as genuinely model-authored engineering judgment instead of the fallback's necessarily more
formulaic output. The automated suite exercises the no-key failure-to-fallback path through the
real `LlmBackedAgent` and `ClaudeClient` wiring. A successful live Anthropic response is
intentionally not tested because the automated suite does not require external credentials.

### 4. Single-process concurrency model

Each `Run` is guarded by its own intrinsic lock (`stateLock()`), held only for short bookkeeping —
never across an agent call or a sleep — so independent stages within one topological batch (TESTING
+ DOCUMENTATION) genuinely run in parallel on separate virtual threads. This is real concurrency,
correctly synchronized, within one JVM. What it is *not*: distributed. There's no work-stealing
across multiple orchestrator instances, no distributed lock, no exactly-once delivery guarantee if
the process crashes mid-stage. For a prototype meant to demonstrate orchestration logic, that's the
right scope; a production version would put `Run` state behind a real datastore with optimistic
concurrency and make `executeStageWithRetry` idempotent/resumable across restarts.

### 5. Guardrails and codebase reasoning use simple heuristics, not real scanners

`SecurityGuardrail` is a keyword denylist, not a SAST tool; `CodebaseReasoningAgent` ranks files by
literal keyword match, not semantic/AST analysis or a dependency graph. Both are explicitly framed
in their own Javadoc as stand-ins that demonstrate *where* a real tool would plug into the pipeline
(the guardrail evaluated at the IMPLEMENTATION entry gate; the reasoning stage's output feeding
ARCHITECTURE_DESIGN) rather than attempts to replicate what a real static-analysis product does.
Swapping in a real SAST/secrets scanner or a language-server-backed reference index is a
same-interface change (`PolicyGuardrail`, `Agent`), not a redesign.

### 6. `BLOCKED_GUARDRAIL` is intentionally terminal

Once a guardrail blocks a stage, the run is terminal (see `Run.isTerminal()` and its comment) —
there is no API that lets a human approval bypass the policy decision. A production extension
could allow an operator to correct the requirement or configuration and start a new governed run,
while preserving the blocked run and its audit history as immutable evidence. This keeps approval
checkpoints and policy enforcement independent: humans control high-impact progression, but they
do not silently supersede security, compliance, or change-control guardrails.

### 7. Only one re-planning trigger is wired up

`ReplanningEngine.evaluateAfter` currently only reacts to `REQUIREMENT_UNDERSTANDING` flagging
ambiguity. The mechanism (inspect a completed stage's `StageOutput`, mutate the live
`DependencyGraph`, record a `ReplanEvent`) is generic — a second trigger (e.g.
`CODEBASE_REASONING` discovering an unplanned-for dependency, or `TESTING` discovering a need for a
migration step) would be a new `if` branch in the same method, not new infrastructure.

## Known risks and failure scenarios considered

- **LLM returns malformed JSON** (even with a real key): `LlmBackedAgent.parse` catches the parse
  failure and rethrows as `LlmUnavailableException`, which the engine treats like any other
  transient failure — retried, then falls back. A model that reliably returns malformed output for
  a given stage would exhaust retries every time and always fall back, which is a visible metric
  (`fallbacksUsed`) rather than a silent failure.
- **A stage's agent throws mid-retry due to a real bug, not unavailability** (e.g. a
  `NullPointerException` in `CodebaseReasoningAgent`): treated identically to an LLM failure —
  retried up to `maxAttempts`, then falls back. `CodebaseReasoningAgent` was written defensively
  (per-file try/catch around unreadable files, an overall try/catch around the repo walk) so this
  should be rare in practice, but the retry/fallback path covers it either way.
- **A human approves without reading closely**: covered explicitly by
  `scenarios/appendix-guardrail-block` — `SecurityGuardrail` still blocks after approval, because
  gates and guardrails are independent mechanisms (see `docs/ARCHITECTURE.md` §5).
  `ChangeControlGuardrail` similarly re-verifies (independently of the gate bookkeeping) that a
  human decision actually exists in the lineage before IMPLEMENTATION proceeds.
- **Two concurrent stages both mutate the run's context**: `ExecutionContext.outputs` is a
  `ConcurrentHashMap` and `lineage` is a `CopyOnWriteArrayList`, so concurrent writes from parallel
  TESTING/DOCUMENTATION agent threads are safe without any additional locking.

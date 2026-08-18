# Java 21 and Edge-Case Review

## Java 21 compatibility

Both applications compile with `javac --release 21` and use Java 21 virtual threads. The build and
test scripts accept JDK 21 or newer while always emitting Java 21-compatible bytecode. Compilation
also enables all available lint checks. Check locally:

```bash
java -version
javac -version
```

Both commands must report version 21 or newer.

## Hardened cases

The review added or confirmed coverage for:

- null, blank, malformed, non-HTTP(S), oversized, and credential-bearing destination URLs;
- invalid, duplicate, blank, and malformed aliases/codes;
- zero, negative, excessive, and exact-boundary expiration values;
- unknown, expired, and deactivated redirect behavior;
- invalid rate-limiter configuration, blank client keys, independent clients, limit exhaustion,
  and window reset;
- malformed JSON, non-object JSON, and request bodies larger than 64 KiB;
- missing/invalid scenario types, blank or oversized run fields, and unknown runs/checkpoints;
- missing approvers, invalid decisions, blank clarification answers, duplicate decisions, and
  attempts to mutate terminal runs;
- security/compliance guardrail blocks, bounded retry/fallback, parallel graph joins, dynamic
  replanning, approval rejection, safe stop, and rollback error containment.

## Important production limitations

This remains an assessment prototype. Its repositories and rate limits are process-local and are
lost on restart. `X-Forwarded-For` should only be trusted behind a configured reverse proxy. A
production system should add persistent storage, authentication/authorization, distributed rate
limiting, TLS termination, secrets management, idempotency keys, request tracing, and load tests.

## Verification commands

```bash
./url-shortener/test.sh
./orchestrator/test.sh
```

The suite contains 48 focused tests. Run both commands on JDK 21 or newer before submission and retain the
terminal output as evidence.

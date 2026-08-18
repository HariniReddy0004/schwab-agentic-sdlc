# API Contract

Both applications accept and return JSON unless the URL-shortener redirect endpoint is used.
Errors use the same shape:

```json
{"error":"Human-readable explanation"}
```

Request bodies must be JSON objects and are limited to 64 KiB. Unknown resources return `404`,
invalid input returns `400`, conflicts return `409`, expired/deactivated redirects return `410`,
and exhausted rate limits return `429`.

## URL shortener (`localhost:8081`)

### Create a short URL

`POST /api/v1/urls`

```json
{
  "longUrl": "https://example.com/article",
  "customAlias": "article1",
  "ttlSeconds": 3600
}
```

| Field | Required | Rules |
| --- | --- | --- |
| `longUrl` | Yes | Absolute HTTP/HTTPS URL, maximum 2,048 characters; embedded credentials are rejected. |
| `customAlias` | No | 1-32 letters or numbers; must be unique. |
| `ttlSeconds` | No | Positive integer, maximum 315,360,000 seconds (10 years). |

Returns `201` with the generated code, original URL, public short URL, creation/expiration times,
active state, and click count.

### Redirect and manage links

| Method and path | Success | Behavior |
| --- | --- | --- |
| `GET /{code}` | `302` | Redirects to the destination and records a click asynchronously. |
| `GET /api/v1/urls/{code}` | `200` | Returns link metadata. |
| `GET /api/v1/urls/{code}/analytics` | `200` | Returns total/recorded clicks, last-24-hour clicks, UTC day-of-week counts, all referrer counts, and the top five referrers. |
| `DELETE /api/v1/urls/{code}` | `200` | Deactivates the link without deleting its history. |
| `GET /health` | `200` | Returns service health. |

## Agentic orchestrator (`localhost:8080`)

### Start a run

`POST /api/v1/runs`

```json
{
  "scenarioType": "BROWNFIELD",
  "title": "Rate limit redirects",
  "requirementText": "Add per-IP rate limiting to the redirect endpoint.",
  "repoContextPath": "/absolute/path/to/url-shortener",
  "requestedBy": "reviewer"
}
```

| Field | Required | Rules |
| --- | --- | --- |
| `scenarioType` | Yes | `GREENFIELD`, `BROWNFIELD`, or `AMBIGUOUS`. |
| `title` | No | Defaults to `Untitled requirement`; 1-200 characters when supplied. |
| `requirementText` | Yes | Nonblank; maximum 20,000 characters. |
| `repoContextPath` | Brownfield only | Repository inspected by the codebase-reasoning stage. |
| `requestedBy` | No | Audit label; defaults to `unknown`. |

Returns `201` with the run identifier and current state.

### Inspect and control runs

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/runs` | List runs. |
| `GET /api/v1/runs/{runId}` | Read state, stage statuses, approvals, and replans. |
| `GET /api/v1/runs/{runId}/graph` | Read graph nodes and legal parallel batches. |
| `GET /api/v1/runs/{runId}/outputs` | Read each stage's generated engineering output. |
| `GET /api/v1/runs/{runId}/lineage` | Read decisions, actors, and rationale. |
| `GET /api/v1/runs/{runId}/audit` | Read execution, gate, guardrail, retry, and rollback events. |
| `GET /api/v1/metrics` | Read success, retry, rollback, MTTR, and latency metrics. |
| `GET /health` | Read orchestrator health and active-run count. |

### Decide an approval

`POST /api/v1/runs/{runId}/approvals/{checkpointId}`

```json
{
  "decision": "APPROVED",
  "approver": "reviewer",
  "comment": "Scope and validation plan are acceptable."
}
```

`decision` must be `APPROVED` or `REJECTED`, and `approver` must be nonblank. A clarification
checkpoint also requires a meaningful answer in `comment`. A checkpoint can be decided only once.

### Safely stop a run

`POST /api/v1/runs/{runId}/safe-stop`

```json
{"reason":"Pause before release while a dependency is investigated."}
```

The operation is terminal and audited. Completed, failed, blocked, or previously stopped runs
cannot be mutated afterward.

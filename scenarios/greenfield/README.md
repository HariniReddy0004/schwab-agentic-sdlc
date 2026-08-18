# Scenario: Greenfield — QR code endpoint for short links

## Requirement (as submitted)

> Add a new GET /api/v1/urls/{code}/qrcode endpoint that returns a QR code image encoding the
> short URL, so users can share short links visually in print or presentations. Should 404 for
> unknown codes and 410 for expired ones, consistent with the existing redirect behavior.

This is a genuinely new capability (no existing endpoint does this), well-defined enough that the
requirement-understanding stage did **not** flag it as ambiguous — it names the HTTP method, path,
response type, and two explicit error cases up front.

## What happened, end to end

Run id: `run-21e49b48` (raw evidence in `01_*.json` .. `09_graph.json` in this directory).

1. **Requirement Understanding** — normalized the ask into a problem statement; `ambiguous=false`
   (confidence 0.85 in the fallback engine, since the text has none of the vagueness markers and is
   well over the 12-word floor).
2. **Task Decomposition** — broke the work into 8 sequenced tasks (schema/response shape, service
   logic, endpoint wiring + validation, unit tests, integration tests, docs, release assessment).
   No CODEBASE_REASONING stage — this is a greenfield graph, there is nothing existing to reason
   about yet (see `09_graph.json`: the node is simply absent).
3. **Architecture & Design** — proposed extending the existing controller/service/repository
   layering rather than a parallel structure, with an explicit rejected-alternative note (embedding
   logic in the controller directly).
4. **Implementation — human approval checkpoint (`IMPLEMENTATION:ENTRY`)** — the run paused here
   (`state: WAITING_APPROVAL`, see `02_waiting_for_implementation_approval.json`) because writing
   code is a high-impact action. A human (`harini`) reviewed the design and approved
   (`03_implementation_approved.json`) before any code-generation stage ran.
5. **Testing** and **Documentation** ran as a real parallel fork — both depend only on
   IMPLEMENTATION, both appear in the same topological batch in `09_graph.json`'s
   `parallelBatches`.
6. **Release Readiness — human approval checkpoint (`RELEASE_READINESS:EXIT`)** — the run paused a
   second time (`04_waiting_for_release_approval.json`) before being considered complete, this time
   as a release sign-off gate rather than a start-of-work gate. The `ComplianceGuardrail` had
   already independently verified TESTING and DOCUMENTATION both produced non-empty artifacts
   before RELEASE_READINESS was even allowed to run.
7. Final state: `COMPLETED` (`05_final_status.json`), with two recorded human decisions in
   `allCheckpoints`, a full decision lineage in `07_decision_lineage.json`, and a complete audit
   trail in `08_audit_trail.json`.

## Decomposition

See `06_stage_outputs.json` → `TASK_DECOMPOSITION.artifacts.taskList` for the actual generated task
list.

## Orchestration points demonstrated

- Two distinct human approval checkpoints (entry gate before high-impact work; exit gate before
  release), both enforced by the DAG engine, not by convention.
- A real fork/join: TESTING and DOCUMENTATION run concurrently and RELEASE_READINESS blocks until
  both report back.
- Governance guardrails (security, compliance, change-control) evaluated automatically before every
  stage, visible as `guardrail_evaluated` audit events even though none of them fired here.

## Validation

- `ComplianceGuardrail` mechanically enforced that release readiness cannot be assessed without
  both test and documentation evidence already existing in the run's context — not a checklist a
  human could forget.
- Every stage attempt, retry, and fallback is in `08_audit_trail.json`, so a reviewer can
  reconstruct exactly what happened and why without trusting a human's summary of it.

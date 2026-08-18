# Scenario: Ambiguous — "Make analytics better"

## Requirement (as submitted, verbatim)

> Make the analytics better.

Five words, no metric, no scope, no named endpoint. This is the scenario designed to prove the
orchestrator does not silently guess at underspecified intent.

## What happened, end to end

Run id: `run-ec87faff` (raw evidence in `01_*.json` .. `09_graph.json`).

1. **Requirement Understanding** ran and — correctly — flagged `ambiguous=true`. The fallback
   engine's ambiguity heuristic matched both signals it looks for: the vagueness marker "better",
   and the requirement being under its 12-word floor. It produced three concrete clarifying
   questions rather than three guesses (see `02_waiting_for_clarification.json` →
   `replans[0].reason`).
2. **Dynamic re-plan** — this is the requirement's "dynamically re-plan when upstream outputs
   change" behavior made concrete, not a scripted per-scenario special case. Immediately after
   REQUIREMENT_UNDERSTANDING succeeded, `ReplanningEngine.evaluateAfter` inspected its output,
   spliced a new `CLARIFICATION` node into the run's live `DependencyGraph`, and rewired
   `TASK_DECOMPOSITION` to depend on it. `02_waiting_for_clarification.json` shows this graph
   mutation already reflected in `stageStatuses` (`CLARIFICATION: WAITING_APPROVAL`) and recorded
   as a first-class `replans[]` entry with a timestamp and reason — compare this to
   `../greenfield/05_final_status.json`, whose `replans` array is empty, because that run's
   requirement never triggered the condition.
3. **Clarification — human approval checkpoint** — the run blocked (`state: WAITING_APPROVAL`)
   until a human supplied an answer. The answer was submitted as the `comment` field on the
   `CLARIFICATION:ENTRY` approval decision (`03_clarification_answered.json`):
   > "By better analytics I mean: add clicks-by-day-of-week and top-5-referrers breakdowns to the
   > existing analytics endpoint, and make it a P1 for next release."
4. The clarification answer was captured into the run's shared `ExecutionContext`
   (`clarificationAnswer`) and is visible verbatim in every downstream stage's prior-context
   summary from that point on — `06_stage_outputs.json` → `CLARIFICATION.summary` shows it was
   incorporated ("Incorporated human clarification: ...") rather than re-guessed.
5. From there the run proceeds through the same graph shape as greenfield (this was a GREENFIELD-
   shaped ambiguous requirement, not a brownfield one) — Task Decomposition (now with a resolved,
   concrete scope), Architecture & Design, **Implementation approval gate**, Testing/Documentation
   in parallel, **Release Readiness exit gate**, `COMPLETED` (`05_final_status.json`).

## Orchestration points demonstrated

- Re-planning is a first-class, auditable event (`replans[]`, plus a `replanned` audit entry in
  `08_audit_trail.json`), not a silent internal branch.
- The same DAG engine, gates, and guardrails that ran the other two scenarios ran this one — the
  only difference is a runtime graph mutation triggered by an agent's own output, exactly as
  specified: "dynamically re-plan when upstream outputs change while maintaining governance."
- CLARIFICATION itself is gated by a human-approval checkpoint like IMPLEMENTATION and
  RELEASE_READINESS — the orchestrator will not silently proceed on a guessed interpretation, it
  physically cannot advance past `TASK_DECOMPOSITION` until a human resolves the checkpoint (the
  graph made `TASK_DECOMPOSITION` depend on `CLARIFICATION` at replan time).

## Validation

- The ambiguity **detector** ran before any design/implementation work started, so no effort was
  wasted building against a guessed interpretation.
- The human's answer is preserved verbatim in the lineage and context, so the eventual
  implementation can be traced back to an explicit decision, not an inferred one.

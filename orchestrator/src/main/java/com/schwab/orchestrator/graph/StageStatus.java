package com.schwab.orchestrator.graph;

public enum StageStatus {
    PENDING,
    WAITING_APPROVAL,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED_GUARDRAIL,
    SKIPPED,
    ROLLED_BACK
}

package com.schwab.orchestrator.model;

public enum RunState {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    BLOCKED_GUARDRAIL,
    FAILED,
    ROLLED_BACK,
    SAFE_STOPPED,
    COMPLETED
}

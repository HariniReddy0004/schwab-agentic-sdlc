package com.schwab.orchestrator.graph;

/** Whether a stage requires a human decision before it may run (entry) or before its output may be relied on downstream (exit). */
public enum GateType {
    NONE,
    HUMAN_APPROVAL
}

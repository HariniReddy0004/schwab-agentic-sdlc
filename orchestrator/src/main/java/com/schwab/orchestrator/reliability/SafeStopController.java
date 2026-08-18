package com.schwab.orchestrator.reliability;

import com.schwab.orchestrator.execution.Run;

/**
 * Explicit safe-stop control: a human (or an automated policy) can halt a run between stage
 * boundaries. In-flight stage work is allowed to finish (it does not kill threads mid-call — that
 * would risk leaving external side effects half-applied); no further stages are scheduled.
 */
public final class SafeStopController {

    public void requestStop(Run run, String reason) {
        run.requestSafeStop(reason);
    }

    public boolean isStopRequested(Run run) {
        return run.isSafeStopRequested();
    }
}

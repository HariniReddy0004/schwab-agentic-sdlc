package com.schwab.orchestrator.reliability;

/** Bounded retry with exponential backoff. Bounded by design: an agent stage never retries forever. */
public final class RetryPolicy {
    private final int maxAttempts;
    private final long baseDelayMillis;
    private final double backoffMultiplier;

    public RetryPolicy(int maxAttempts, long baseDelayMillis, double backoffMultiplier) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMillis = baseDelayMillis;
        this.backoffMultiplier = backoffMultiplier;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public long delayBeforeAttempt(int attemptNumberJustFailed) {
        double delay = baseDelayMillis * Math.pow(backoffMultiplier, attemptNumberJustFailed - 1);
        return (long) Math.min(delay, 5_000); // cap so a demo run never stalls for long
    }
}

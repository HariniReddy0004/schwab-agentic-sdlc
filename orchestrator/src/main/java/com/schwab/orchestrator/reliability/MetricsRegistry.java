package com.schwab.orchestrator.reliability;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide reliability metrics, exactly the ones the assignment calls out by name: success
 * rate, retry/rollback frequency, MTTR, and end-to-end latency. Kept as simple atomic counters +
 * a bounded event log rather than pulling in a metrics library (Micrometer et al. need Maven
 * Central, which this build environment cannot reach).
 */
public final class MetricsRegistry {
    private final AtomicLong runsStarted = new AtomicLong();
    private final AtomicLong runsSucceeded = new AtomicLong();
    private final AtomicLong runsFailed = new AtomicLong();
    private final AtomicLong stagesExecuted = new AtomicLong();
    private final AtomicLong stageRetries = new AtomicLong();
    private final AtomicLong rollbacks = new AtomicLong();
    private final AtomicLong approvalsGranted = new AtomicLong();
    private final AtomicLong approvalsRejected = new AtomicLong();
    private final AtomicLong guardrailBlocks = new AtomicLong();
    private final AtomicLong fallbacksUsed = new AtomicLong();

    /** (failureDetectedAt, recoveredAt) pairs — a stage failing then a retry/fallback succeeding is one "incident". */
    private final ConcurrentLinkedQueue<Duration> recoveryDurations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Duration> runLatencies = new ConcurrentLinkedQueue<>();

    public void runStarted() {
        runsStarted.incrementAndGet();
    }

    public void runSucceeded(Duration e2eLatency) {
        runsSucceeded.incrementAndGet();
        runLatencies.add(e2eLatency);
    }

    public void runFailed(Duration e2eLatency) {
        runsFailed.incrementAndGet();
        runLatencies.add(e2eLatency);
    }

    public void stageExecuted() {
        stagesExecuted.incrementAndGet();
    }

    public void stageRetried() {
        stageRetries.incrementAndGet();
    }

    public void rollbackPerformed() {
        rollbacks.incrementAndGet();
    }

    public void approvalGranted() {
        approvalsGranted.incrementAndGet();
    }

    public void approvalRejected() {
        approvalsRejected.incrementAndGet();
    }

    public void guardrailBlocked() {
        guardrailBlocks.incrementAndGet();
    }

    public void fallbackUsed() {
        fallbacksUsed.incrementAndGet();
    }

    public void recordRecovery(Instant failedAt, Instant recoveredAt) {
        recoveryDurations.add(Duration.between(failedAt, recoveredAt));
    }

    public double successRate() {
        long total = runsSucceeded.get() + runsFailed.get();
        return total == 0 ? 0.0 : (double) runsSucceeded.get() / total;
    }

    public double retryFrequency() {
        long total = stagesExecuted.get();
        return total == 0 ? 0.0 : (double) stageRetries.get() / total;
    }

    public double rollbackFrequency() {
        long total = runsStarted.get();
        return total == 0 ? 0.0 : (double) rollbacks.get() / total;
    }

    public double mttrMillis() {
        List<Duration> durations = List.copyOf(recoveryDurations);
        if (durations.isEmpty()) return 0.0;
        long sum = durations.stream().mapToLong(Duration::toMillis).sum();
        return (double) sum / durations.size();
    }

    public double avgEndToEndLatencyMillis() {
        List<Duration> durations = List.copyOf(runLatencies);
        if (durations.isEmpty()) return 0.0;
        long sum = durations.stream().mapToLong(Duration::toMillis).sum();
        return (double) sum / durations.size();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runsStarted", runsStarted.get());
        m.put("runsSucceeded", runsSucceeded.get());
        m.put("runsFailed", runsFailed.get());
        m.put("successRate", successRate());
        m.put("stagesExecuted", stagesExecuted.get());
        m.put("stageRetries", stageRetries.get());
        m.put("retryFrequency", retryFrequency());
        m.put("rollbacks", rollbacks.get());
        m.put("rollbackFrequency", rollbackFrequency());
        m.put("approvalsGranted", approvalsGranted.get());
        m.put("approvalsRejected", approvalsRejected.get());
        m.put("guardrailBlocks", guardrailBlocks.get());
        m.put("fallbacksUsed", fallbacksUsed.get());
        m.put("mttrMillis", mttrMillis());
        m.put("avgEndToEndLatencyMillis", avgEndToEndLatencyMillis());
        return m;
    }
}

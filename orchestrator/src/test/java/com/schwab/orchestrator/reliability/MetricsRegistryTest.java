package com.schwab.orchestrator.reliability;

import com.schwab.orchestrator.testing.MicroTest;

import java.time.Duration;

public class MetricsRegistryTest {

    @MicroTest.Test
    public void successRateReflectsSucceededVsFailedRuns() {
        MetricsRegistry m = new MetricsRegistry();
        m.runStarted();
        m.runSucceeded(Duration.ofMillis(100));
        m.runStarted();
        m.runFailed(Duration.ofMillis(200));
        m.runStarted();
        m.runSucceeded(Duration.ofMillis(300));

        MicroTest.assertEquals(2.0 / 3.0, m.successRate(), "success rate should be succeeded/(succeeded+failed)");
    }

    @MicroTest.Test
    public void retryFrequencyIsRetriesOverStagesExecuted() {
        MetricsRegistry m = new MetricsRegistry();
        m.stageExecuted();
        m.stageExecuted();
        m.stageRetried();
        MicroTest.assertEquals(0.5, m.retryFrequency(), "retry frequency should be retries / stages executed");
    }

    @MicroTest.Test
    public void mttrAveragesRecoveryDurations() {
        MetricsRegistry m = new MetricsRegistry();
        var t0 = java.time.Instant.parse("2026-01-01T00:00:00Z");
        m.recordRecovery(t0, t0.plusMillis(100));
        m.recordRecovery(t0, t0.plusMillis(300));
        MicroTest.assertEquals(200.0, m.mttrMillis(), "MTTR should average the two recovery durations (100ms, 300ms)");
    }

    @MicroTest.Test
    public void snapshotContainsAllRequiredKeys() {
        MetricsRegistry m = new MetricsRegistry();
        var snap = m.snapshot();
        for (String key : new String[]{"successRate", "retryFrequency", "rollbackFrequency", "mttrMillis", "avgEndToEndLatencyMillis"}) {
            MicroTest.assertTrue(snap.containsKey(key), "metrics snapshot should contain key: " + key);
        }
    }
}

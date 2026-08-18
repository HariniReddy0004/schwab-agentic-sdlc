package com.schwab.urlshortener.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple fixed-window token bucket, per client key (typically client IP). One instance guards one
 * logical operation (e.g. URL creation) so different endpoints can have independent limits.
 *
 * This is intentionally dependency-free (no bucket4j/Resilience4j — unreachable from this build
 * environment). A production deployment behind multiple instances would move this to a shared
 * store (e.g. Redis) so limits are enforced cluster-wide, not per-process.
 */
public final class RateLimiterService {
    private record Window(AtomicLong count, long windowStartMillis) {
    }

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMillis;

    public RateLimiterService(int maxRequests, long windowMillis) {
        if (maxRequests <= 0) throw new IllegalArgumentException("maxRequests must be greater than zero");
        if (windowMillis <= 0) throw new IllegalArgumentException("windowMillis must be greater than zero");
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    public boolean allow(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("rate-limit key is required");
        long now = System.currentTimeMillis();
        Window w = windows.computeIfAbsent(key, k -> new Window(new AtomicLong(0), now));
        synchronized (w) {
            if (now - w.windowStartMillis >= windowMillis) {
                windows.put(key, new Window(new AtomicLong(1), now));
                return true;
            }
            return w.count.incrementAndGet() <= maxRequests;
        }
    }
}

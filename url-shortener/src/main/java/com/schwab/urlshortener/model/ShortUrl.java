package com.schwab.urlshortener.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Core domain entity: a mapping from a short code to a long URL. */
public final class ShortUrl {
    private final String code;
    private final String longUrl;
    private final Instant createdAt;
    private final Instant expiresAt; // nullable = never expires
    private volatile boolean active = true;
    private final boolean customAlias;
    private final AtomicLong clickCount = new AtomicLong(0);

    public ShortUrl(String code, String longUrl, Instant createdAt, Instant expiresAt, boolean customAlias) {
        this.code = code;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.customAlias = customAlias;
    }

    public String code() {
        return code;
    }

    public String longUrl() {
        return longUrl;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public long incrementAndGetClicks() {
        return clickCount.incrementAndGet();
    }

    public long clickCount() {
        return clickCount.get();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("longUrl", longUrl);
        m.put("createdAt", createdAt.toString());
        m.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        m.put("active", active);
        m.put("customAlias", customAlias);
        m.put("clickCount", clickCount.get());
        return m;
    }
}

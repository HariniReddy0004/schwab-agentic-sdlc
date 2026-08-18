package com.schwab.urlshortener.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** An immutable record of a single redirect (click) against a short code. */
public record ClickEvent(String code, Instant timestamp, String referrer, String userAgent, String clientIpHash) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("timestamp", timestamp.toString());
        m.put("referrer", referrer);
        m.put("userAgent", userAgent);
        m.put("clientIpHash", clientIpHash);
        return m;
    }
}

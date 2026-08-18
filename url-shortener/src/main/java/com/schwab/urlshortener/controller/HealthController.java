package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.framework.Router;
import com.schwab.urlshortener.service.UrlShortenerService;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HealthController {
    private final UrlShortenerService service;
    private final long startedAtMillis = System.currentTimeMillis();

    public HealthController(UrlShortenerService service) {
        this.service = service;
    }

    public void register(Router router) {
        router.get("/health", ctx -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "UP");
            body.put("totalShortUrls", service.totalUrlCount());
            body.put("uptimeMs", System.currentTimeMillis() - startedAtMillis);
            ctx.sendJson(200, body);
        });
    }
}

package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.framework.HttpCtx;
import com.schwab.urlshortener.framework.Router;
import com.schwab.urlshortener.model.ShortUrl;
import com.schwab.urlshortener.service.AnalyticsService;
import com.schwab.urlshortener.service.UrlShortenerService;

public final class AnalyticsController {
    private final UrlShortenerService service;
    private final AnalyticsService analytics;

    public AnalyticsController(UrlShortenerService service, AnalyticsService analytics) {
        this.service = service;
        this.analytics = analytics;
    }

    public void register(Router router) {
        router.get("/api/v1/urls/{code}/analytics", this::analyticsFor);
    }

    private void analyticsFor(HttpCtx ctx) {
        String code = ctx.pathParam("code");
        ShortUrl url = service.getMetadata(code); // 404s if unknown, even if expired/deactivated
        ctx.sendJson(200, analytics.summarize(code, url.clickCount()));
    }
}

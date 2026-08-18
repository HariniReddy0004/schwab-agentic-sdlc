package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.framework.HttpCtx;
import com.schwab.urlshortener.framework.Router;
import com.schwab.urlshortener.model.ShortUrl;
import com.schwab.urlshortener.service.AnalyticsService;
import com.schwab.urlshortener.service.RateLimiterService;
import com.schwab.urlshortener.service.UrlShortenerService;

import java.time.Instant;

/** Handles the actual redirect: GET /{code} -> 302 to the long URL, click recorded asynchronously. */
public final class RedirectController {
    private final UrlShortenerService service;
    private final AnalyticsService analytics;
    private final RateLimiterService redirectRateLimiter;

    public RedirectController(UrlShortenerService service, AnalyticsService analytics,
                              RateLimiterService redirectRateLimiter) {
        this.service = service;
        this.analytics = analytics;
        this.redirectRateLimiter = redirectRateLimiter;
    }

    public void register(Router router) {
        router.get("/{code}", this::redirect);
    }

    private void redirect(HttpCtx ctx) {
        if (!redirectRateLimiter.allow(ctx.clientAddress())) {
            throw com.schwab.urlshortener.framework.ApiException.tooManyRequests(
                    "Redirect rate limit exceeded; try again shortly");
        }
        String code = ctx.pathParam("code");
        ShortUrl url = service.resolve(code);
        url.incrementAndGetClicks();
        analytics.recordClickAsync(code, ctx.header("Referer"), ctx.header("User-Agent"), ctx.clientAddress(), Instant.now());
        ctx.redirect(302, url.longUrl());
    }
}

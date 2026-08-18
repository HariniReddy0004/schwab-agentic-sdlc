package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.framework.ApiException;
import com.schwab.urlshortener.framework.Json;
import com.schwab.urlshortener.framework.Router;
import com.schwab.urlshortener.model.ShortUrl;
import com.schwab.urlshortener.service.RateLimiterService;
import com.schwab.urlshortener.service.UrlShortenerService;

import java.util.Map;

/** CRUD-ish API for short URLs: create / fetch metadata / deactivate. */
public final class ShortenController {
    private final UrlShortenerService service;
    private final RateLimiterService createRateLimiter;
    private final String publicBaseUrl;

    public ShortenController(UrlShortenerService service, RateLimiterService createRateLimiter, String publicBaseUrl) {
        this.service = service;
        this.createRateLimiter = createRateLimiter;
        this.publicBaseUrl = publicBaseUrl;
    }

    public void register(Router router) {
        router.post("/api/v1/urls", this::create);
        router.get("/api/v1/urls/{code}", this::getMetadata);
        router.delete("/api/v1/urls/{code}", this::deactivate);
    }

    private void create(com.schwab.urlshortener.framework.HttpCtx ctx) {
        if (!createRateLimiter.allow(ctx.clientAddress())) {
            throw ApiException.tooManyRequests("Rate limit exceeded for URL creation; try again shortly");
        }
        Map<String, Object> body = ctx.bodyAsJson();
        String longUrl = Json.str(body, "longUrl");
        String alias = Json.str(body, "customAlias");
        Long ttl = body.containsKey("ttlSeconds") ? Json.asLong(body.get("ttlSeconds")) : null;

        ShortUrl created = service.createShortUrl(longUrl, alias, ttl);

        Map<String, Object> response = new java.util.LinkedHashMap<>(created.toMap());
        response.put("shortUrl", publicBaseUrl + "/" + created.code());
        ctx.sendJson(201, response);
    }

    private void getMetadata(com.schwab.urlshortener.framework.HttpCtx ctx) {
        ShortUrl url = service.getMetadata(ctx.pathParam("code"));
        ctx.sendJson(200, url.toMap());
    }

    private void deactivate(com.schwab.urlshortener.framework.HttpCtx ctx) {
        service.deactivate(ctx.pathParam("code"));
        ctx.sendJson(200, Map.of("code", ctx.pathParam("code"), "deactivated", true));
    }
}

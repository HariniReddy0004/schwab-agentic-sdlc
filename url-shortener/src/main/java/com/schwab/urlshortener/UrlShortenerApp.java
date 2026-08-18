package com.schwab.urlshortener;

import com.schwab.urlshortener.controller.AnalyticsController;
import com.schwab.urlshortener.controller.HealthController;
import com.schwab.urlshortener.controller.RedirectController;
import com.schwab.urlshortener.controller.ShortenController;
import com.schwab.urlshortener.framework.Router;
import com.schwab.urlshortener.framework.WebServer;
import com.schwab.urlshortener.repository.ClickEventRepository;
import com.schwab.urlshortener.repository.InMemoryShortUrlRepository;
import com.schwab.urlshortener.repository.ShortUrlRepository;
import com.schwab.urlshortener.service.AnalyticsService;
import com.schwab.urlshortener.service.Base62Encoder;
import com.schwab.urlshortener.service.RateLimiterService;
import com.schwab.urlshortener.service.UrlShortenerService;

import java.time.Clock;

/** Composition root: wires repositories -> services -> controllers -> router -> HTTP server. */
public final class UrlShortenerApp {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
        String publicBaseUrl = System.getenv().getOrDefault("PUBLIC_BASE_URL", "http://localhost:" + port);

        ShortUrlRepository shortUrlRepository = new InMemoryShortUrlRepository();
        ClickEventRepository clickEventRepository = new ClickEventRepository();

        UrlShortenerService urlShortenerService = new UrlShortenerService(shortUrlRepository, new Base62Encoder(), Clock.systemUTC());
        AnalyticsService analyticsService = new AnalyticsService(clickEventRepository);
        RateLimiterService createRateLimiter = new RateLimiterService(30, 60_000); // 30 creates/min/IP
        RateLimiterService redirectRateLimiter = new RateLimiterService(300, 60_000); // 300 redirects/min/IP

        Router router = new Router();
        router.onAccess(event -> System.out.println("[access] " + event));

        new ShortenController(urlShortenerService, createRateLimiter, publicBaseUrl).register(router);
        new AnalyticsController(urlShortenerService, analyticsService).register(router);
        new HealthController(urlShortenerService).register(router);
        // Redirect route is registered last: it is the most permissive pattern (/{code}) and must
        // not shadow the more specific /api/v1/... routes above (Router matches in registration order).
        new RedirectController(urlShortenerService, analyticsService, redirectRateLimiter).register(router);

        WebServer server = new WebServer(port, router);
        server.start();
        System.out.println("url-shortener listening on port " + server.port() + " (public base url: " + publicBaseUrl + ")");
    }
}

package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.framework.Json;
import com.schwab.urlshortener.framework.Router;
import com.schwab.urlshortener.framework.WebServer;
import com.schwab.urlshortener.repository.ClickEventRepository;
import com.schwab.urlshortener.repository.InMemoryShortUrlRepository;
import com.schwab.urlshortener.repository.ShortUrlRepository;
import com.schwab.urlshortener.service.AnalyticsService;
import com.schwab.urlshortener.service.Base62Encoder;
import com.schwab.urlshortener.service.RateLimiterService;
import com.schwab.urlshortener.service.UrlShortenerService;
import com.schwab.urlshortener.testing.MicroTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Map;

/**
 * Full-stack integration test: boots the real HttpServer on an ephemeral port and drives it with
 * java.net.http.HttpClient, exercising the whole stack (router -> controller -> service -> repo)
 * exactly as a real client would, including redirect semantics and error status codes.
 */
public class HttpApiIntegrationTest {

    private WebServer bootServer() {
        return bootServer(1000);
    }

    private WebServer bootServer(int redirectLimit) {
        ShortUrlRepository repo = new InMemoryShortUrlRepository();
        UrlShortenerService urlService = new UrlShortenerService(repo, new Base62Encoder(), Clock.systemUTC());
        AnalyticsService analyticsService = new AnalyticsService(new ClickEventRepository());
        RateLimiterService rateLimiter = new RateLimiterService(1000, 60_000);

        Router router = new Router();
        new ShortenController(urlService, rateLimiter, "http://localhost").register(router);
        new AnalyticsController(urlService, analyticsService).register(router);
        new HealthController(urlService).register(router);
        new RedirectController(urlService, analyticsService, new RateLimiterService(redirectLimit, 60_000)).register(router);

        WebServer server = new WebServer(0, router);
        server.start();
        return server;
    }

    @MicroTest.Test
    public void createThenRedirectThenAnalyticsRoundTrip() throws Exception {
        WebServer server = bootServer();
        try {
            HttpClient client = HttpClient.newHttpClient();
            int port = server.port();

            String createBody = Json.write(Map.of("longUrl", "https://www.schwab.com/research", "customAlias", "research1"));
            HttpRequest createReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(createBody))
                    .build();
            HttpResponse<String> createResp = client.send(createReq, HttpResponse.BodyHandlers.ofString());
            MicroTest.assertEquals(201, createResp.statusCode(), "create should return 201");
            Map<String, Object> created = Json.parseObject(createResp.body());
            MicroTest.assertEquals("research1", created.get("code"), "custom alias should be echoed back as code");

            HttpClient noRedirectClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest redirectReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/research1")).GET().build();
            HttpResponse<Void> redirectResp = noRedirectClient.send(redirectReq, HttpResponse.BodyHandlers.discarding());
            MicroTest.assertEquals(302, redirectResp.statusCode(), "redirect should return 302");
            MicroTest.assertEquals("https://www.schwab.com/research", redirectResp.headers().firstValue("Location").orElse(null),
                    "Location header should point at the original long url");

            // analytics is recorded asynchronously; poll briefly instead of a fixed sleep-and-hope
            Map<String, Object> analytics = null;
            for (int i = 0; i < 20; i++) {
                HttpRequest analyticsReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls/research1/analytics")).GET().build();
                HttpResponse<String> analyticsResp = client.send(analyticsReq, HttpResponse.BodyHandlers.ofString());
                MicroTest.assertEquals(200, analyticsResp.statusCode(), "analytics should return 200");
                analytics = Json.parseObject(analyticsResp.body());
                if (((Number) analytics.get("recordedEvents")).intValue() >= 1) break;
                Thread.sleep(25);
            }
            MicroTest.assertNotNull(analytics, "analytics response should not be null");
            MicroTest.assertTrue(((Number) analytics.get("totalClicks")).longValue() >= 1, "totalClicks should reflect the redirect we just followed");
            MicroTest.assertTrue(analytics.containsKey("clicksByDayOfWeekUtc"), "analytics should include UTC day-of-week breakdown");
            MicroTest.assertTrue(analytics.containsKey("topReferrers"), "analytics should include top referrers");
        } finally {
            server.stop();
        }
    }

    @MicroTest.Test
    public void unknownCodeReturns404() throws Exception {
        WebServer server = bootServer();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/doesNotExist12")).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            MicroTest.assertEquals(404, resp.statusCode(), "unknown short code should 404");
        } finally {
            server.stop();
        }
    }

    @MicroTest.Test
    public void invalidLongUrlReturns400() throws Exception {
        WebServer server = bootServer();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String body = Json.write(Map.of("longUrl", "not-a-url"));
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/v1/urls"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            MicroTest.assertEquals(400, resp.statusCode(), "invalid longUrl should 400");
        } finally {
            server.stop();
        }
    }

    @MicroTest.Test
    public void deactivatedCodeReturns410OnRedirectAttempt() throws Exception {
        WebServer server = bootServer();
        try {
            HttpClient client = HttpClient.newHttpClient();
            int port = server.port();
            String createBody = Json.write(Map.of("longUrl", "https://example.com/x", "customAlias", "deacttest"));
            client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(createBody)).build(),
                    HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> deactivateResp = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls/deacttest")).DELETE().build(),
                    HttpResponse.BodyHandlers.ofString());
            MicroTest.assertEquals(200, deactivateResp.statusCode(), "deactivate should return 200");

            HttpResponse<String> redirectAttempt = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/deacttest")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            MicroTest.assertEquals(410, redirectAttempt.statusCode(), "redirect attempt on deactivated code should be 410 Gone");
        } finally {
            server.stop();
        }
    }

    @MicroTest.Test
    public void redirectRateLimitReturns429WhenExhausted() throws Exception {
        WebServer server = bootServer(1);
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            int port = server.port();
            String createBody = Json.write(Map.of("longUrl", "https://example.com/limited", "customAlias", "limited1"));
            client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/urls"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(createBody)).build(),
                    HttpResponse.BodyHandlers.ofString());

            HttpRequest redirect = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/limited1")).GET().build();
            MicroTest.assertEquals(302, client.send(redirect, HttpResponse.BodyHandlers.ofString()).statusCode(),
                    "first redirect should be allowed");
            MicroTest.assertEquals(429, client.send(redirect, HttpResponse.BodyHandlers.ofString()).statusCode(),
                    "second redirect from the same client should be rate limited");
        } finally {
            server.stop();
        }
    }

    public static void main(String[] args) throws IOException {
        // allows running this single class directly for debugging
    }
}

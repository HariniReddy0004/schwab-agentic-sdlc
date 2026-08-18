package com.schwab.urlshortener.service;

import com.schwab.urlshortener.framework.ApiException;
import com.schwab.urlshortener.model.ShortUrl;
import com.schwab.urlshortener.repository.InMemoryShortUrlRepository;
import com.schwab.urlshortener.repository.ShortUrlRepository;
import com.schwab.urlshortener.testing.MicroTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public class UrlShortenerServiceTest {

    private UrlShortenerService newService() {
        return new UrlShortenerService(new InMemoryShortUrlRepository(), new Base62Encoder(), Clock.systemUTC());
    }

    @MicroTest.Test
    public void createsShortUrlForValidHttpsLink() {
        UrlShortenerService svc = newService();
        ShortUrl url = svc.createShortUrl("https://www.schwab.com/pricing", null, null);
        MicroTest.assertNotNull(url.code(), "generated code should not be null");
        MicroTest.assertEquals(7, url.code().length(), "default generated code length");
        MicroTest.assertEquals("https://www.schwab.com/pricing", url.longUrl(), "long url should round-trip");
    }

    @MicroTest.Test
    public void rejectsMissingLongUrl() {
        UrlShortenerService svc = newService();
        MicroTest.assertThrows(ApiException.class, () -> svc.createShortUrl(null, null, null), "missing longUrl should be rejected");
    }

    @MicroTest.Test
    public void rejectsNonHttpScheme() {
        UrlShortenerService svc = newService();
        MicroTest.assertThrows(ApiException.class, () -> svc.createShortUrl("ftp://example.com/file", null, null), "non-http(s) scheme should be rejected");
    }

    @MicroTest.Test
    public void honorsCustomAliasAndRejectsDuplicate() {
        UrlShortenerService svc = newService();
        ShortUrl first = svc.createShortUrl("https://example.com/a", "mybrand", null);
        MicroTest.assertEquals("mybrand", first.code(), "custom alias should be used verbatim as code");
        MicroTest.assertThrows(ApiException.class, () -> svc.createShortUrl("https://example.com/b", "mybrand", null),
                "reusing an existing alias should conflict");
    }

    @MicroTest.Test
    public void rejectsInvalidAliasCharacters() {
        UrlShortenerService svc = newService();
        MicroTest.assertThrows(ApiException.class, () -> svc.createShortUrl("https://example.com/a", "bad alias!", null),
                "alias with invalid characters should be rejected");
    }

    @MicroTest.Test
    public void rejectsZeroNegativeAndExcessiveTtl() {
        UrlShortenerService svc = newService();
        MicroTest.assertThrows(ApiException.class, () -> svc.createShortUrl("https://example.com", null, 0L), "zero TTL should be rejected");
        MicroTest.assertThrows(ApiException.class, () -> svc.createShortUrl("https://example.com", null, -1L), "negative TTL should be rejected");
        MicroTest.assertThrows(ApiException.class, () -> svc.createShortUrl("https://example.com", null, 315_360_001L), "TTL over ten years should be rejected");
    }

    @MicroTest.Test
    public void rejectsEmbeddedCredentialsAndMalformedCodes() {
        UrlShortenerService svc = newService();
        MicroTest.assertThrows(ApiException.class, () -> svc.createShortUrl("https://user:secret@example.com/private", null, null), "embedded credentials should be rejected");
        MicroTest.assertThrows(ApiException.class, () -> svc.resolve("bad/code"), "malformed code should be rejected");
        MicroTest.assertThrows(ApiException.class, () -> svc.getMetadata(""), "blank code should be rejected");
    }

    @MicroTest.Test
    public void expiresExactlyAtBoundary() {
        ShortUrlRepository repo = new InMemoryShortUrlRepository();
        Instant createdAt = Instant.parse("2030-01-01T00:00:00Z");
        new UrlShortenerService(repo, new Base62Encoder(), Clock.fixed(createdAt, ZoneOffset.UTC))
                .createShortUrl("https://example.com/boundary", "boundary", 10L);
        UrlShortenerService atExpiry = new UrlShortenerService(repo, new Base62Encoder(),
                Clock.fixed(createdAt.plusSeconds(10), ZoneOffset.UTC));
        MicroTest.assertThrows(ApiException.class, () -> atExpiry.resolve("boundary"), "URL should expire at the exact expiration instant");
    }

    @MicroTest.Test
    public void resolveThrowsNotFoundForUnknownCode() {
        UrlShortenerService svc = newService();
        MicroTest.assertThrows(ApiException.class, () -> svc.resolve("doesNotExist"), "unknown code should 404");
    }

    @MicroTest.Test
    public void resolveThrowsGoneForExpiredUrl() {
        ShortUrlRepository repo = new InMemoryShortUrlRepository();
        // Clock fixed far in the future relative to an already-past expiry, to deterministically simulate expiration.
        Clock fixedNow = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        UrlShortenerService svc = new UrlShortenerService(repo, new Base62Encoder(), fixedNow);
        Clock past = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        UrlShortenerService creatorAtPastTime = new UrlShortenerService(repo, new Base62Encoder(), past);
        ShortUrl created = creatorAtPastTime.createShortUrl("https://example.com/expiring", "expiretest", 10L);
        MicroTest.assertThrows(ApiException.class, () -> svc.resolve(created.code()), "expired short url should be Gone");
    }

    @MicroTest.Test
    public void deactivateBlocksFutureResolution() {
        UrlShortenerService svc = newService();
        ShortUrl created = svc.createShortUrl("https://example.com/deactivate-me", "deactivateme", null);
        svc.deactivate(created.code());
        MicroTest.assertThrows(ApiException.class, () -> svc.resolve(created.code()), "deactivated url should not resolve");
    }

    @MicroTest.Test
    public void deactivateUnknownCodeThrowsNotFound() {
        UrlShortenerService svc = newService();
        MicroTest.assertThrows(ApiException.class, () -> svc.deactivate("nope"), "deactivating unknown code should 404");
    }
}

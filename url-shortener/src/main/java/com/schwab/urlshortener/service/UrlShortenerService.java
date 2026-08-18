package com.schwab.urlshortener.service;

import com.schwab.urlshortener.framework.ApiException;
import com.schwab.urlshortener.model.ShortUrl;
import com.schwab.urlshortener.repository.ShortUrlRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Optional;

/** Core business logic: validation, code generation/collision handling, expiration, lifecycle. */
public final class UrlShortenerService {
    private static final int DEFAULT_CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 5;
    private static final long MAX_TTL_SECONDS = Duration.ofDays(3650).toSeconds();

    private final ShortUrlRepository repository;
    private final Base62Encoder encoder;
    private final Clock clock;

    public UrlShortenerService(ShortUrlRepository repository, Base62Encoder encoder, Clock clock) {
        this.repository = repository;
        this.encoder = encoder;
        this.clock = clock;
    }

    public ShortUrl createShortUrl(String longUrl, String requestedAlias, Long ttlSeconds) {
        validateLongUrl(longUrl);
        Instant now = clock.instant();
        Instant expiresAt = null;
        if (ttlSeconds != null) {
            if (ttlSeconds <= 0) {
                throw ApiException.badRequest("ttlSeconds must be greater than zero");
            }
            if (ttlSeconds > MAX_TTL_SECONDS) {
                throw ApiException.badRequest("ttlSeconds cannot exceed 10 years");
            }
            try {
                expiresAt = now.plusSeconds(ttlSeconds);
            } catch (RuntimeException e) {
                throw ApiException.badRequest("ttlSeconds produces an invalid expiration time");
            }
        }

        if (requestedAlias != null && !requestedAlias.isBlank()) {
            if (!encoder.isValidAlias(requestedAlias)) {
                throw ApiException.badRequest("Alias must be 1-32 Base62 characters (letters/digits)");
            }
            ShortUrl candidate = new ShortUrl(requestedAlias, longUrl, now, expiresAt, true);
            if (!repository.insertIfAbsent(candidate)) {
                throw ApiException.conflict("Alias '" + requestedAlias + "' is already in use");
            }
            return candidate;
        }

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = encoder.generate(DEFAULT_CODE_LENGTH);
            ShortUrl candidate = new ShortUrl(code, longUrl, now, expiresAt, false);
            if (repository.insertIfAbsent(candidate)) {
                return candidate;
            }
            // collision: loop and retry with a fresh random code (bounded, so we never spin forever)
        }
        throw new IllegalStateException("Exhausted " + MAX_GENERATION_ATTEMPTS + " attempts generating a unique short code");
    }

    public ShortUrl resolve(String code) {
        validateCode(code);
        ShortUrl url = repository.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("No short URL found for code '" + code + "'"));
        if (!url.isActive()) {
            throw ApiException.gone("Short URL '" + code + "' has been deactivated");
        }
        if (url.isExpired(clock.instant())) {
            throw ApiException.gone("Short URL '" + code + "' expired at " + url.expiresAt());
        }
        return url;
    }

    public ShortUrl getMetadata(String code) {
        validateCode(code);
        return repository.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("No short URL found for code '" + code + "'"));
    }

    public void deactivate(String code) {
        validateCode(code);
        if (!repository.exists(code)) {
            throw ApiException.notFound("No short URL found for code '" + code + "'");
        }
        repository.deactivate(code);
    }

    public long totalUrlCount() {
        return repository.count();
    }

    private void validateLongUrl(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw ApiException.badRequest("longUrl is required");
        }
        if (longUrl.length() > 2048) {
            throw ApiException.badRequest("longUrl exceeds maximum length of 2048 characters");
        }
        URI uri;
        try {
            uri = new URI(longUrl);
        } catch (URISyntaxException e) {
            throw ApiException.badRequest("longUrl is not a valid URI: " + e.getMessage());
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw ApiException.badRequest("longUrl must use http or https scheme");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw ApiException.badRequest("longUrl must include a host");
        }
        if (uri.getUserInfo() != null) {
            throw ApiException.badRequest("longUrl must not contain embedded credentials");
        }
    }

    private void validateCode(String code) {
        if (code == null || !encoder.isValidAlias(code)) {
            throw ApiException.badRequest("code must be 1-32 Base62 characters (letters/digits)");
        }
    }
}

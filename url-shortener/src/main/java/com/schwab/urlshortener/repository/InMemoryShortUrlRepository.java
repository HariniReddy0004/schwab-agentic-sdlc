package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.model.ShortUrl;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory, thread-safe store for short URLs.
 *
 * Trade-off (documented in TESTING_AND_TRADEOFFS.md): a real deployment would back this with a
 * durable store (e.g. a key-value or relational database) for durability across restarts and to
 * share state across multiple instances. Because this build environment cannot reach Maven
 * Central for a JDBC/H2 driver, persistence here is process-local. The interface boundary
 * (ShortUrlRepository) is what makes swapping in a real database a non-invasive change.
 */
public final class InMemoryShortUrlRepository implements ShortUrlRepository {
    private final ConcurrentMap<String, ShortUrl> store = new ConcurrentHashMap<>();

    @Override
    public boolean insertIfAbsent(ShortUrl shortUrl) {
        return store.putIfAbsent(shortUrl.code(), shortUrl) == null;
    }

    @Override
    public Optional<ShortUrl> findByCode(String code) {
        return Optional.ofNullable(store.get(code));
    }

    @Override
    public boolean exists(String code) {
        return store.containsKey(code);
    }

    @Override
    public void deactivate(String code) {
        ShortUrl u = store.get(code);
        if (u != null) u.deactivate();
    }

    @Override
    public long count() {
        return store.size();
    }
}

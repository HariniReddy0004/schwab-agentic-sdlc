package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.model.ShortUrl;

import java.util.Optional;

public interface ShortUrlRepository {
    /** Returns false if the code already exists (used to detect Base62 collisions / alias conflicts). */
    boolean insertIfAbsent(ShortUrl shortUrl);

    Optional<ShortUrl> findByCode(String code);

    boolean exists(String code);

    void deactivate(String code);

    long count();
}

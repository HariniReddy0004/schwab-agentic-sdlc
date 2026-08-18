package com.schwab.urlshortener.service;

import com.schwab.urlshortener.testing.MicroTest;

public class RateLimiterServiceTest {
    @MicroTest.Test
    public void rejectsInvalidConfigurationAndBlankKeys() {
        MicroTest.assertThrows(IllegalArgumentException.class, () -> new RateLimiterService(0, 1000), "zero request limit should fail fast");
        MicroTest.assertThrows(IllegalArgumentException.class, () -> new RateLimiterService(1, 0), "zero window should fail fast");
        RateLimiterService limiter = new RateLimiterService(1, 1000);
        MicroTest.assertThrows(IllegalArgumentException.class, () -> limiter.allow(" "), "blank client key should fail fast");
    }

    @MicroTest.Test
    public void allowsUpToLimitThenBlocks() {
        RateLimiterService limiter = new RateLimiterService(3, 60_000);
        MicroTest.assertTrue(limiter.allow("1.2.3.4"), "1st request should be allowed");
        MicroTest.assertTrue(limiter.allow("1.2.3.4"), "2nd request should be allowed");
        MicroTest.assertTrue(limiter.allow("1.2.3.4"), "3rd request should be allowed");
        MicroTest.assertFalse(limiter.allow("1.2.3.4"), "4th request should be blocked");
    }

    @MicroTest.Test
    public void tracksDifferentKeysIndependently() {
        RateLimiterService limiter = new RateLimiterService(1, 60_000);
        MicroTest.assertTrue(limiter.allow("a"), "key a first request allowed");
        MicroTest.assertFalse(limiter.allow("a"), "key a second request blocked");
        MicroTest.assertTrue(limiter.allow("b"), "key b should have its own independent budget");
    }

    @MicroTest.Test
    public void resetsAfterWindowExpires() throws InterruptedException {
        RateLimiterService limiter = new RateLimiterService(1, 50);
        MicroTest.assertTrue(limiter.allow("x"), "first request allowed");
        MicroTest.assertFalse(limiter.allow("x"), "second request in same window blocked");
        Thread.sleep(80);
        MicroTest.assertTrue(limiter.allow("x"), "request after window expiry should be allowed again");
    }
}

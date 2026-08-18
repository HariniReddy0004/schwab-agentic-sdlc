package com.schwab.urlshortener.testing;

import com.schwab.urlshortener.controller.HttpApiIntegrationTest;
import com.schwab.urlshortener.service.Base62EncoderTest;
import com.schwab.urlshortener.service.AnalyticsServiceTest;
import com.schwab.urlshortener.service.RateLimiterServiceTest;
import com.schwab.urlshortener.service.UrlShortenerServiceTest;

public final class TestRunnerMain {
    public static void main(String[] args) {
        System.out.println("Running url-shortener test suite...\n");
        var results = MicroTest.run(
                Base62EncoderTest.class,
                AnalyticsServiceTest.class,
                RateLimiterServiceTest.class,
                UrlShortenerServiceTest.class,
                HttpApiIntegrationTest.class
        );
        int failed = MicroTest.report(results);
        if (failed > 0) System.exit(1);
    }
}

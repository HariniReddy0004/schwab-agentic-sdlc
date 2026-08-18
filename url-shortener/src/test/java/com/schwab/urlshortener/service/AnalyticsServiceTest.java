package com.schwab.urlshortener.service;

import com.schwab.urlshortener.model.ClickEvent;
import com.schwab.urlshortener.repository.ClickEventRepository;
import com.schwab.urlshortener.testing.MicroTest;

import java.time.Instant;
import java.util.Map;

public class AnalyticsServiceTest {

    @SuppressWarnings("unchecked")
    @MicroTest.Test
    public void returnsDayOfWeekBreakdownAndDeterministicTopFiveReferrers() {
        ClickEventRepository repository = new ClickEventRepository();
        Instant monday = Instant.parse("2030-01-07T12:00:00Z");
        String[] referrers = {"alpha", "alpha", "alpha", "beta", "beta", "gamma", "delta", "epsilon", "zeta"};
        for (String referrer : referrers) {
            repository.record(new ClickEvent("stats1", monday, referrer, "test-agent", "hash"));
        }

        Map<String, Object> result = new AnalyticsService(repository).summarize("stats1", referrers.length);
        Map<String, Long> byDay = (Map<String, Long>) result.get("clicksByDayOfWeekUtc");
        Map<String, Long> top = (Map<String, Long>) result.get("topReferrers");

        MicroTest.assertEquals(9L, byDay.get("MONDAY"), "all fixed events should be grouped under Monday UTC");
        MicroTest.assertEquals(5, top.size(), "only five referrers should be returned");
        MicroTest.assertEquals(3L, top.get("alpha"), "highest referrer should retain its count");
        MicroTest.assertTrue(!top.containsKey("zeta"), "deterministic tie-breaking should exclude the last one-click referrer");
    }
}

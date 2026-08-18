package com.schwab.urlshortener.service;

import com.schwab.urlshortener.model.ClickEvent;
import com.schwab.urlshortener.repository.ClickEventRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Records clicks asynchronously (so a redirect is never slowed down by analytics bookkeeping) and summarizes them. */
public final class AnalyticsService {
    private final ClickEventRepository repository;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AnalyticsService(ClickEventRepository repository) {
        this.repository = repository;
    }

    public void recordClickAsync(String code, String referrer, String userAgent, String clientAddress, Instant timestamp) {
        executor.submit(() -> {
            ClickEvent event = new ClickEvent(code, timestamp,
                    referrer == null ? "direct" : referrer,
                    userAgent == null ? "unknown" : userAgent,
                    hash(clientAddress));
            repository.record(event);
        });
    }

    public Map<String, Object> summarize(String code, long liveClickCount) {
        List<ClickEvent> events = repository.forCode(code);
        Instant now = Instant.now();
        long last24h = events.stream().filter(e -> ChronoUnit.HOURS.between(e.timestamp(), now) < 24).count();

        Map<String, Long> byReferrer = new LinkedHashMap<>();
        Map<String, Long> byDayOfWeek = new LinkedHashMap<>();
        for (ClickEvent e : events) {
            byReferrer.merge(e.referrer(), 1L, Long::sum);
            String day = e.timestamp().atZone(ZoneOffset.UTC).getDayOfWeek().name();
            byDayOfWeek.merge(day, 1L, Long::sum);
        }

        Map<String, Long> topReferrers = new LinkedHashMap<>();
        byReferrer.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .forEach(entry -> topReferrers.put(entry.getKey(), entry.getValue()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("totalClicks", liveClickCount);
        out.put("recordedEvents", events.size());
        out.put("clicksLast24h", last24h);
        out.put("clicksByReferrer", byReferrer);
        out.put("clicksByDayOfWeekUtc", byDayOfWeek);
        out.put("topReferrers", topReferrers);
        return out;
    }

    private static String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unhashed";
        }
    }
}

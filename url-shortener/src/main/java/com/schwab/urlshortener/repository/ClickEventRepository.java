package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.model.ClickEvent;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/** In-memory click-event log, keyed by short code, used to drive the analytics endpoint. */
public final class ClickEventRepository {
    private final ConcurrentMap<String, ConcurrentLinkedQueue<ClickEvent>> byCode = new ConcurrentHashMap<>();

    public void record(ClickEvent event) {
        byCode.computeIfAbsent(event.code(), k -> new ConcurrentLinkedQueue<>()).add(event);
    }

    public List<ClickEvent> forCode(String code) {
        ConcurrentLinkedQueue<ClickEvent> q = byCode.get(code);
        return q == null ? List.of() : List.copyOf(q);
    }
}

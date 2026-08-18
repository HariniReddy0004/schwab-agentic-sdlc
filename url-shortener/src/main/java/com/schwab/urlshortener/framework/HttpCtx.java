package com.schwab.urlshortener.framework;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thin request/response wrapper around {@link HttpExchange}, in the spirit of a Spring MVC request context. */
public final class HttpCtx {
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private byte[] cachedBody;

    public HttpCtx(HttpExchange exchange, Map<String, String> pathParams) {
        this.exchange = exchange;
        this.pathParams = pathParams;
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    public String path() {
        return exchange.getRequestURI().getPath();
    }

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public Map<String, String> queryParams() {
        Map<String, String> out = new LinkedHashMap<>();
        String q = exchange.getRequestURI().getRawQuery();
        if (q == null || q.isBlank()) return out;
        for (String pair : q.split("&")) {
            int idx = pair.indexOf('=');
            String k = idx >= 0 ? pair.substring(0, idx) : pair;
            String v = idx >= 0 ? pair.substring(idx + 1) : "";
            out.put(urlDecode(k), urlDecode(v));
        }
        return out;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public String clientAddress() {
        String fwd = header("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        InetSocketAddress addr = exchange.getRemoteAddress();
        return addr == null ? "unknown" : addr.getAddress().getHostAddress();
    }

    public byte[] bodyBytes() {
        if (cachedBody != null) return cachedBody;
        try (InputStream is = exchange.getRequestBody(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = is.read(buffer)) != -1; ) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw ApiException.payloadTooLarge("Request body exceeds 64 KiB");
                }
                bos.write(buffer, 0, read);
            }
            cachedBody = bos.toByteArray();
            return cachedBody;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read request body", e);
        }
    }

    public String bodyAsString() {
        byte[] b = bodyBytes();
        return b.length == 0 ? "" : new String(b, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> bodyAsJson() {
        String body = bodyAsString();
        if (body.isBlank()) return Map.of();
        try {
            Object parsed = Json.parse(body);
            if (!(parsed instanceof Map<?, ?> parsedMap)) {
                throw ApiException.badRequest("JSON request body must be an object");
            }
            return (Map<String, Object>) parsedMap;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Malformed JSON request body: " + e.getMessage());
        }
    }

    public void sendJson(int status, Object payload) {
        byte[] bytes = Json.write(payload).getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.getResponseBody().close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write response", e);
        }
    }

    public void redirect(int status, String location) {
        try {
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(status, -1);
            exchange.getResponseBody().close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write redirect", e);
        }
    }

    public void setResponseHeader(String name, String value) {
        exchange.getResponseHeaders().set(name, value);
    }
}

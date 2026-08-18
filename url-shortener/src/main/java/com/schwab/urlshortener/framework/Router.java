package com.schwab.urlshortener.framework;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal path-templated router (`/api/v1/urls/{code}`) that dispatches to {@link RouteHandler}s
 * and centralizes JSON error handling — the same job Spring MVC's DispatcherServlet +
 * @ExceptionHandler would do, written by hand since no framework jar is available here.
 */
public final class Router implements HttpHandler {

    @FunctionalInterface
    public interface RouteHandler {
        void handle(HttpCtx ctx) throws Exception;
    }

    private record Route(String method, Pattern pattern, List<String> paramNames, RouteHandler handler) {
    }

    private final List<Route> routes = new ArrayList<>();
    private Consumer<Map<String, Object>> accessLogger = event -> {
    };

    public void onAccess(Consumer<Map<String, Object>> logger) {
        this.accessLogger = logger;
    }

    public void get(String path, RouteHandler handler) {
        add("GET", path, handler);
    }

    public void post(String path, RouteHandler handler) {
        add("POST", path, handler);
    }

    public void delete(String path, RouteHandler handler) {
        add("DELETE", path, handler);
    }

    public void put(String path, RouteHandler handler) {
        add("PUT", path, handler);
    }

    private void add(String method, String path, RouteHandler handler) {
        List<String> names = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) continue;
            regex.append('/');
            if (segment.startsWith("{") && segment.endsWith("}")) {
                names.add(segment.substring(1, segment.length() - 1));
                regex.append("([^/]+)");
            } else {
                regex.append(Pattern.quote(segment));
            }
        }
        if (regex.isEmpty() || !regex.toString().contains("/")) {
            regex.append("/?");
        }
        regex.append("/?$");
        routes.add(new Route(method, Pattern.compile(regex.toString()), names, handler));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long start = System.nanoTime();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        int status = 500;
        try {
            Route matched = null;
            Matcher matcher = null;
            boolean pathMatchedAnyMethod = false;
            for (Route r : routes) {
                Matcher m = r.pattern.matcher(path);
                if (m.matches()) {
                    pathMatchedAnyMethod = true;
                    if (r.method.equals(method)) {
                        matched = r;
                        matcher = m;
                        break;
                    }
                }
            }
            HttpCtx ctx;
            if (matched == null) {
                ctx = new HttpCtx(exchange, Map.of());
                status = pathMatchedAnyMethod ? 405 : 404;
                ctx.sendJson(status, Map.of(
                        "error", pathMatchedAnyMethod ? "METHOD_NOT_ALLOWED" : "NOT_FOUND",
                        "message", pathMatchedAnyMethod ? "Method not allowed for this resource" : "No route matches " + path));
                return;
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (int i = 0; i < matched.paramNames.size(); i++) {
                params.put(matched.paramNames.get(i), matcher.group(i + 1));
            }
            ctx = new HttpCtx(exchange, params);
            try {
                matched.handler.handle(ctx);
                status = 200; // handlers that already wrote a response override this only for logging purposes
            } catch (ApiException e) {
                status = e.status();
                ctx.sendJson(e.status(), Map.of("error", e.code(), "message", e.getMessage()));
            } catch (Exception e) {
                status = 500;
                ctx.sendJson(500, Map.of("error", "INTERNAL_ERROR", "message", String.valueOf(e.getMessage())));
            }
        } finally {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("method", method);
            event.put("path", path);
            event.put("status", status);
            event.put("tookMs", tookMs);
            accessLogger.accept(event);
        }
    }
}

package com.schwab.urlshortener.framework;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bootstraps the built-in JDK HTTP server (jdk.httpserver module) with a {@link Router}. */
public final class WebServer {
    private final HttpServer server;
    private final ExecutorService executor;

    public WebServer(int port, Router router) {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new RuntimeException("Failed to bind port " + port, e);
        }
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", router);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    public int port() {
        return server.getAddress().getPort();
    }
}

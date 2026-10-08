package com.knot.backend.testsupport;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class LlmHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String requestBody;
    private volatile String authorization;

    public LlmHttpServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(
                        "127.0.0.1",
                        0
                ),
                0
        );
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
    }

    public URI baseUrl() {
        return URI.create(
                "http://127.0.0.1:" + server.getAddress()
                        .getPort()
        );
    }

    public void respond(
            int status,
            String body
    ) {
        respond(exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(
                    status,
                    bytes.length
            );
            exchange.getResponseBody()
                    .write(bytes);
        });
    }

    public void respond(HttpHandler handler) {
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    requests.incrementAndGet();
                    requestBody = new String(
                            exchange.getRequestBody()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    );
                    authorization = exchange.getRequestHeaders()
                            .getFirst("Authorization");
                    try {
                        handler.handle(exchange);
                    } finally {
                        exchange.close();
                    }
                }
        );
    }

    public int requestCount() {
        return requests.get();
    }

    public String requestBody() {
        return requestBody;
    }

    public String authorization() {
        return authorization;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}

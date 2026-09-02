package com.knot.backend.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.chat.infrastructure.LlmProperties;
import com.knot.backend.search.application.DocumentEmbeddingClient;
import com.knot.backend.search.application.EmbeddingProperties;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenAiCompatibleEmbeddingClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("OpenAI 호환 embedding 응답을 순서대로 변환하고 token은 요청 본문에 넣지 않는다")
    void embed_success_parsesVectorsAndProtectsToken() throws Exception {
        // given
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        URI baseUri = startServer(exchange -> {
            requestBody.set(
                    new String(
                            exchange.getRequestBody()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    )
            );
            authorization.set(
                    exchange.getRequestHeaders()
                            .getFirst("Authorization")
            );
            respond(
                    exchange,
                    200,
                    "{\"data\":[{\"index\":0,\"embedding\":[1.0,0.0]},{\"index\":1,\"embedding\":[0.0,1.0]}]}"
            );
        });
        DocumentEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                HttpClient.newHttpClient(),
                objectMapper,
                new LlmProperties(
                        baseUri,
                        "embedding-secret",
                        "qwen/qwen3.6-27b",
                        512,
                        0.2,
                        Duration.ofSeconds(5)
                ),
                new EmbeddingProperties(
                        "qwen-embedding",
                        2
                )
        );

        // when
        List<double[]> embeddings = client.embed(
                List.of(
                        "첫 질문",
                        "두 번째 질문"
                )
        );

        // then
        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0)).containsExactly(
                1.0,
                0.0
        );
        assertThat(embeddings.get(1)).containsExactly(
                0.0,
                1.0
        );
        assertThat(authorization).hasValue("Bearer embedding-secret");
        JsonNode payload = objectMapper.readTree(requestBody.get());
        assertThat(
                payload.get("model")
                        .asString()
        ).isEqualTo("qwen-embedding");
        assertThat(
                payload.get("input")
                        .size()
        ).isEqualTo(2);
        assertThat(payload.toString()).doesNotContain("embedding-secret");
    }

    @Test
    @DisplayName("embedding 서버가 비정상 응답을 주면 내부 검색 오류로 변환한다")
    void embed_failure_providerStatus() throws Exception {
        // given
        URI baseUri = startServer(
                exchange -> respond(
                        exchange,
                        401,
                        "unauthorized"
                )
        );
        DocumentEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                HttpClient.newHttpClient(),
                objectMapper,
                new LlmProperties(
                        baseUri,
                        "embedding-secret",
                        "qwen/qwen3.6-27b",
                        512,
                        0.2,
                        Duration.ofSeconds(5)
                ),
                new EmbeddingProperties(
                        "qwen-embedding",
                        2
                )
        );

        // when
        ThrowingCallable action = () -> client.embed(List.of("질문"));

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
    }

    private URI startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(
                        "localhost",
                        0
                ),
                0
        );
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext(
                "/v1/embeddings",
                exchange -> {
                    try (exchange) {
                        handler.handle(exchange);
                    }
                }
        );
        server.start();
        return URI.create(
                "http://localhost:" + server.getAddress()
                        .getPort() + "/v1"
        );
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(
                status,
                bytes.length
        );
        exchange.getResponseBody()
                .write(bytes);
    }

    @FunctionalInterface
    private interface ExchangeHandler {

        void handle(HttpExchange exchange) throws IOException;
    }
}

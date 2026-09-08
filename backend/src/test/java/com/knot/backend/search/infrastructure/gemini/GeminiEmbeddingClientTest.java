package com.knot.backend.search.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.knot.backend.search.application.DocumentEmbeddingClient;
import com.knot.backend.search.application.EmbeddingProperties;
import com.knot.backend.search.application.EmbeddingTask;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GeminiEmbeddingClientTest {
    private static final String BATCH_PATH = "/v1beta/models/gemini-embedding-001:batchEmbedContents";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Duration> sleeps = new ArrayList<>();
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
    @DisplayName("batchEmbedContents 응답을 순서대로 L2 정규화해 돌려주고 키는 header에만 넣는다")
    void embed_success_normalizesVectorsAndProtectsKey() throws Exception {
        // given
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        URI baseUri = startServer(exchange -> {
            requestBody.set(readBody(exchange));
            apiKeyHeader.set(
                    exchange.getRequestHeaders()
                            .getFirst("x-goog-api-key")
            );
            requestPath.set(
                    exchange.getRequestURI()
                            .getPath()
            );
            respond(
                    exchange,
                    200,
                    "{\"embeddings\":[{\"values\":[3.0,4.0]},{\"values\":[0.0,2.0]}]}"
            );
        });
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when
        List<double[]> embeddings = client.embed(
                List.of(
                        "첫 문서",
                        "둘째 문서"
                ),
                EmbeddingTask.DOCUMENT
        );

        // then
        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0)).containsExactly(
                new double[]{0.6, 0.8},
                within(1e-12)
        );
        assertThat(embeddings.get(1)).containsExactly(
                new double[]{0.0, 1.0},
                within(1e-12)
        );
        assertThat(apiKeyHeader).hasValue("gemini-secret");
        assertThat(requestPath).hasValue(BATCH_PATH);
        JsonNode requests = objectMapper.readTree(requestBody.get())
                .get("requests");
        assertThat(requests.size()).isEqualTo(2);
        JsonNode first = requests.get(0);
        assertThat(
                first.get("model")
                        .asString()
        ).isEqualTo("models/gemini-embedding-001");
        assertThat(
                first.get("taskType")
                        .asString()
        ).isEqualTo("RETRIEVAL_DOCUMENT");
        assertThat(
                first.get("outputDimensionality")
                        .asInt()
        ).isEqualTo(2);
        assertThat(
                first.get("content")
                        .get("parts")
                        .get(0)
                        .get("text")
                        .asString()
        ).isEqualTo("첫 문서");
        assertThat(requestBody.get()).doesNotContain("gemini-secret");
    }

    @Test
    @DisplayName("질의 임베딩은 taskType을 RETRIEVAL_QUERY로 보낸다")
    void embed_success_queryTaskType() throws Exception {
        // given
        AtomicReference<String> requestBody = new AtomicReference<>();
        URI baseUri = startServer(exchange -> {
            requestBody.set(readBody(exchange));
            respond(
                    exchange,
                    200,
                    "{\"embeddings\":[{\"values\":[1.0,0.0]}]}"
            );
        });
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when
        client.embed(
                List.of("우리 DB 뭐 쓰기로 했지?"),
                EmbeddingTask.QUERY
        );

        // then
        JsonNode first = objectMapper.readTree(requestBody.get())
                .get("requests")
                .get(0);
        assertThat(
                first.get("taskType")
                        .asString()
        ).isEqualTo("RETRIEVAL_QUERY");
    }

    @Test
    @DisplayName("빈 입력이면 요청 없이 빈 목록을 돌려준다")
    void embed_success_emptyInputSkipsRequest() throws Exception {
        // given
        AtomicInteger requestCount = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(
                    exchange,
                    200,
                    "{\"embeddings\":[]}"
            );
        });
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when
        List<double[]> embeddings = client.embed(
                List.of(),
                EmbeddingTask.DOCUMENT
        );

        // then
        assertThat(embeddings).isEmpty();
        assertThat(requestCount).hasValue(0);
    }

    @Test
    @DisplayName("401·403은 검색 설정 오류로 변환한다")
    void embed_failure_unauthorizedMapsToConfigurationInvalid() throws Exception {
        // given
        URI baseUri = startServer(
                exchange -> respond(
                        exchange,
                        403,
                        "{\"error\":{\"code\":403,\"message\":\"denied\",\"status\":\"PERMISSION_DENIED\"}}"
                )
        );
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when
        ThrowingCallable action = () -> client.embed(
                List.of("질문"),
                EmbeddingTask.QUERY
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode())
                        .isEqualTo(SearchErrorCode.SEARCH_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("질의 임베딩의 429는 재시도 없이 provider 실패로 변환한다")
    void embed_failure_rateLimitedQueryIsNotRetried() throws Exception {
        // given
        AtomicInteger requestCount = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(
                    exchange,
                    429,
                    "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"
            );
        });
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when
        ThrowingCallable action = () -> client.embed(
                List.of("질문"),
                EmbeddingTask.QUERY
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
        assertThat(requestCount).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("색인 배치의 429·503은 지수 백오프로 재시도한 뒤 성공 응답을 돌려준다")
    void embed_success_retriesRateLimitedIndexingBatch() throws Exception {
        // given
        AtomicInteger requestCount = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            int attempt = requestCount.incrementAndGet();
            if (attempt == 1) {
                respond(
                        exchange,
                        429,
                        "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"
                );
                return;
            }
            if (attempt == 2) {
                respond(
                        exchange,
                        503,
                        "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"
                );
                return;
            }
            respond(
                    exchange,
                    200,
                    "{\"embeddings\":[{\"values\":[3.0,4.0]}]}"
            );
        });
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when
        List<double[]> embeddings = client.embed(
                List.of("문서"),
                EmbeddingTask.DOCUMENT
        );

        // then
        assertThat(embeddings).hasSize(1);
        assertThat(embeddings.get(0)).containsExactly(
                new double[]{0.6, 0.8},
                within(1e-12)
        );
        assertThat(requestCount).hasValue(3);
        assertThat(sleeps).containsExactly(
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
        );
    }

    @Test
    @DisplayName("색인 배치가 최대 시도 횟수까지 429면 provider 실패로 변환한다")
    void embed_failure_rateLimitedIndexingGivesUpAfterMaxAttempts() throws Exception {
        // given
        AtomicInteger requestCount = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(
                    exchange,
                    429,
                    "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"
            );
        });
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when
        ThrowingCallable action = () -> client.embed(
                List.of("문서"),
                EmbeddingTask.DOCUMENT
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
        assertThat(requestCount).hasValue(3);
        assertThat(sleeps).containsExactly(
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
        );
    }

    @Test
    @DisplayName("색인 배치라도 400·500은 재시도하지 않는다")
    void embed_failure_nonTransientIndexingErrorIsNotRetried() throws Exception {
        // given
        AtomicInteger requestCount = new AtomicInteger();
        AtomicInteger status = new AtomicInteger(400);
        URI baseUri = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(
                    exchange,
                    status.get(),
                    "{\"error\":{\"code\":" + status.get() + ",\"status\":\"INVALID_ARGUMENT\"}}"
            );
        });
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when & then
        for (int code : new int[]{400, 500}) {
            status.set(code);
            assertThatThrownBy(
                    () -> client.embed(
                            List.of("문서"),
                            EmbeddingTask.DOCUMENT
                    )
            ).isInstanceOfSatisfying(
                    SearchException.class,
                    exception -> assertThat(exception.searchErrorCode())
                            .isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
            );
        }
        assertThat(requestCount).hasValue(2);
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("응답 차원이 설정과 다르면 provider 실패로 변환한다")
    void embed_failure_dimensionMismatch() throws Exception {
        // given
        URI baseUri = startServer(
                exchange -> respond(
                        exchange,
                        200,
                        "{\"embeddings\":[{\"values\":[1.0,2.0,3.0]}]}"
                )
        );
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when
        ThrowingCallable action = () -> client.embed(
                List.of("질문"),
                EmbeddingTask.DOCUMENT
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
    }

    @Test
    @DisplayName("응답 건수가 요청과 다르거나 영벡터면 provider 실패로 변환한다")
    void embed_failure_countMismatchOrZeroVector() throws Exception {
        // given
        AtomicReference<String> body = new AtomicReference<>("{\"embeddings\":[{\"values\":[1.0,0.0]}]}");
        URI baseUri = startServer(
                exchange -> respond(
                        exchange,
                        200,
                        body.get()
                )
        );
        DocumentEmbeddingClient client = client(
                baseUri,
                "gemini-secret",
                2
        );

        // when
        ThrowingCallable countMismatch = () -> client.embed(
                List.of(
                        "하나",
                        "둘"
                ),
                EmbeddingTask.DOCUMENT
        );

        // then
        assertThatThrownBy(countMismatch).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );

        // given
        body.set("{\"embeddings\":[{\"values\":[0.0,0.0]}]}");

        // when
        ThrowingCallable zeroVector = () -> client.embed(
                List.of("하나"),
                EmbeddingTask.DOCUMENT
        );

        // then
        assertThatThrownBy(zeroVector).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
    }

    @Test
    @DisplayName("API 키가 비어 있으면 요청 없이 검색 설정 오류로 실패한다")
    void embed_failure_blankKeySkipsRequest() throws Exception {
        // given
        AtomicInteger requestCount = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(
                    exchange,
                    200,
                    "{\"embeddings\":[{\"values\":[1.0,0.0]}]}"
            );
        });
        DocumentEmbeddingClient client = client(
                baseUri,
                "",
                2
        );

        // when
        ThrowingCallable action = () -> client.embed(
                List.of("질문"),
                EmbeddingTask.QUERY
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode())
                        .isEqualTo(SearchErrorCode.SEARCH_CONFIGURATION_INVALID)
        );
        assertThat(requestCount).hasValue(0);
    }

    private DocumentEmbeddingClient client(
            URI baseUri,
            String apiKey,
            int dimensions
    ) {
        return new GeminiEmbeddingClient(
                HttpClient.newHttpClient(),
                objectMapper,
                new GeminiEmbeddingProperties(
                        baseUri,
                        apiKey,
                        "gemini-embedding-001",
                        Duration.ofSeconds(5),
                        3,
                        Duration.ofSeconds(5)
                ),
                new EmbeddingProperties(
                        "unused-for-gemini",
                        dimensions
                ),
                sleeps::add
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
                BATCH_PATH,
                exchange -> {
                    try (exchange) {
                        handler.handle(exchange);
                    }
                }
        );
        server.start();
        return URI.create(
                "http://localhost:" + server.getAddress()
                        .getPort()
        );
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(
                exchange.getRequestBody()
                        .readAllBytes(),
                StandardCharsets.UTF_8
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

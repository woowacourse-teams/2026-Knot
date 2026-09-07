package com.knot.backend.chat.infrastructure.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.chat.application.LlmStream;
import com.knot.backend.chat.application.dto.command.LlmMessage;
import com.knot.backend.chat.application.dto.command.LlmMessageRole;
import com.knot.backend.chat.application.dto.command.LlmRequest;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AnthropicLlmClientTest {
    private static final String API_KEY = "anthropic-secret";

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
    @DisplayName("Messages API SSE 응답을 chunk로 전달하고 API 키는 x-api-key 헤더에만 보낸다")
    void start_success_streamsChunksAndSendsRequest() throws Exception {
        // given
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        AtomicReference<String> versionHeader = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        URI baseUri = startServer(exchange -> {
            requestBody.set(
                    new String(
                            exchange.getRequestBody()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    )
            );
            apiKeyHeader.set(
                    exchange.getRequestHeaders()
                            .getFirst("x-api-key")
            );
            versionHeader.set(
                    exchange.getRequestHeaders()
                            .getFirst("anthropic-version")
            );
            authorizationHeader.set(
                    exchange.getRequestHeaders()
                            .getFirst("Authorization")
            );
            respond(
                    exchange,
                    200,
                    """
                            event: message_start
                            data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5","content":[],"usage":{"input_tokens":25,"output_tokens":1}}}

                            event: content_block_start
                            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"첫 "}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"응답"}}

                            event: content_block_stop
                            data: {"type":"content_block_stop","index":0}

                            event: message_delta
                            data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":12}}

                            event: message_stop
                            data: {"type":"message_stop"}

                            """
            );
        });
        AnthropicLlmClient client = client(baseUri);

        // when
        LlmStream stream = client.start(
                new LlmRequest(
                        List.of(
                                new LlmMessage(
                                        LlmMessageRole.SYSTEM,
                                        "근거 규칙"
                                ),
                                new LlmMessage(
                                        LlmMessageRole.USER,
                                        "질문"
                                )
                        )
                )
        );

        // then
        assertThat(stream.next()).isEqualTo("첫 ");
        assertThat(stream.next()).isEqualTo("응답");
        assertThat(stream.hasNext()).isFalse();
        assertThat(apiKeyHeader).hasValue(API_KEY);
        assertThat(versionHeader).hasValue("2023-06-01");
        assertThat(authorizationHeader.get()).isNull();
        JsonNode payload = objectMapper.readTree(requestBody.get());
        assertThat(
                payload.get("model")
                        .asString()
        ).isEqualTo("claude-opus-5");
        assertThat(
                payload.get("max_tokens")
                        .asInt()
        ).isEqualTo(4096);
        assertThat(
                payload.get("stream")
                        .asBoolean()
        ).isTrue();
        assertThat(
                payload.get("system")
                        .asString()
        ).isEqualTo("근거 규칙");
        assertThat(
                payload.get("messages")
                        .get(0)
                        .get("role")
                        .asString()
        ).isEqualTo("user");
        assertThat(
                payload.get("output_config")
                        .get("effort")
                        .asString()
        ).isEqualTo("medium");
        assertThat(payload.has("temperature")).isFalse();
        assertThat(payload.has("thinking")).isFalse();
        assertThat(payload.toString()).doesNotContain(API_KEY);
    }

    @ParameterizedTest(name = "HTTP {0} → {1}")
    @DisplayName("성공이 아닌 상태 코드는 설계된 오류 코드로 매핑한다")
    @CsvSource({"401, LLM_CONFIGURATION_INVALID", "403, LLM_CONFIGURATION_INVALID", "429, LLM_RATE_LIMITED",
            "529, LLM_RATE_LIMITED", "400, LLM_STREAM_FAILED", "500, LLM_STREAM_FAILED"})
    void start_failure_mapsStatusCode(
            int status,
            ChatErrorCode expected
    ) throws Exception {
        // given
        URI baseUri = startServer(exchange -> {
            exchange.getResponseHeaders()
                    .set(
                            "Retry-After",
                            "7"
                    );
            respond(
                    exchange,
                    status,
                    "{\"type\":\"error\",\"error\":{\"type\":\"some_error\",\"message\":\"failed\"}}"
            );
        });
        AnthropicLlmClient client = client(baseUri);

        // when & then
        assertThatThrownBy(() -> client.start(new LlmRequest(List.of()))).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.chatErrorCode()).isEqualTo(expected)
        );
    }

    @Test
    @DisplayName("응답 header가 request-timeout 안에 오지 않으면 LLM_STREAM_TIMEOUT으로 실패한다")
    void start_failure_headerTimeout() throws Exception {
        // given
        CountDownLatch releaseResponse = new CountDownLatch(1);
        URI baseUri = startServer(exchange -> {
            try {
                releaseResponse.await(
                        5,
                        TimeUnit.SECONDS
                );
            } catch (InterruptedException exception) {
                Thread.currentThread()
                        .interrupt();
            }
        });
        AnthropicLlmClient client = client(
                baseUri,
                Duration.ofMillis(300)
        );

        try {
            // when & then
            assertThatThrownBy(() -> client.start(new LlmRequest(List.of()))).isInstanceOfSatisfying(
                    ChatException.class,
                    exception -> assertThat(exception.chatErrorCode()).isEqualTo(ChatErrorCode.LLM_STREAM_TIMEOUT)
            );
        } finally {
            releaseResponse.countDown();
        }
    }

    @Test
    @DisplayName("응답 body가 멈춘 상태에서 스트림을 닫으면 대기 중인 읽기가 즉시 끝난다")
    void stream_closeWhileReading_unblocksConsumer() throws Exception {
        // given
        AtomicReference<HttpExchange> responseExchange = new AtomicReference<>();
        CountDownLatch responseStarted = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        URI baseUri = startServer(exchange -> {
            responseExchange.set(exchange);
            exchange.getResponseHeaders()
                    .set(
                            "Content-Type",
                            "text/event-stream"
                    );
            exchange.sendResponseHeaders(
                    200,
                    0
            );
            exchange.getResponseBody()
                    .write("event: ping\ndata: {\"type\":\"ping\"}\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody()
                    .flush();
            responseStarted.countDown();
            try {
                releaseResponse.await(
                        5,
                        TimeUnit.SECONDS
                );
            } catch (InterruptedException exception) {
                Thread.currentThread()
                        .interrupt();
            }
        });
        AnthropicLlmClient client = client(baseUri);
        LlmStream stream = client.start(new LlmRequest(List.of()));
        ExecutorService consumer = Executors.newSingleThreadExecutor();
        Future<Boolean> hasNext = consumer.submit(stream::hasNext);

        try {
            assertThat(
                    responseStarted.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
            assertThatThrownBy(
                    () -> hasNext.get(
                            200,
                            TimeUnit.MILLISECONDS
                    )
            ).isInstanceOf(TimeoutException.class);

            // when
            stream.close();

            // then
            assertThat(
                    hasNext.get(
                            2,
                            TimeUnit.SECONDS
                    )
            ).isFalse();
        } finally {
            stream.close();
            releaseResponse.countDown();
            HttpExchange exchange = responseExchange.get();
            if (exchange != null) {
                exchange.close();
            }
            consumer.shutdownNow();
            assertThat(
                    consumer.awaitTermination(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
        }
    }

    private AnthropicLlmClient client(URI baseUri) {
        return client(
                baseUri,
                Duration.ofSeconds(5)
        );
    }

    private AnthropicLlmClient client(
            URI baseUri,
            Duration requestTimeout
    ) {
        return new AnthropicLlmClient(
                HttpClient.newHttpClient(),
                objectMapper,
                new AnthropicLlmProperties(
                        baseUri,
                        API_KEY,
                        "claude-opus-5",
                        "medium",
                        4096,
                        requestTimeout
                )
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
                "/v1/messages",
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

    private void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "text/event-stream"
                );
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

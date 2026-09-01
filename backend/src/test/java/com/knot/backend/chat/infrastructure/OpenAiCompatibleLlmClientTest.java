package com.knot.backend.chat.infrastructure;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenAiCompatibleLlmClientTest {
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
    @DisplayName("OpenAI 호환 SSE 응답을 chunk로 전달하고 인증값은 HTTP 헤더에만 보낸다")
    void start_success_streamsChunksAndSendsRequest() throws Exception {
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
                    """
                            data: {"choices":[{"delta":{"content":"첫 "}}]}

                            data: {"choices":[{"delta":{"content":"응답"}}]}

                            data: [DONE]

                            """
            );
        });
        LlmProperties properties = new LlmProperties(
                baseUri,
                "server-token",
                "qwen/qwen3.6-27b",
                512,
                0.2,
                Duration.ofSeconds(5)
        );
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                HttpClient.newHttpClient(),
                objectMapper,
                properties
        );

        // when
        LlmStream stream = client.start(
                new LlmRequest(
                        List.of(
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
        assertThat(authorization).hasValue("Bearer server-token");
        JsonNode payload = objectMapper.readTree(requestBody.get());
        assertThat(
                payload.get("model")
                        .asString()
        ).isEqualTo("qwen/qwen3.6-27b");
        assertThat(
                payload.get("stream")
                        .asBoolean()
        ).isTrue();
        assertThat(
                payload.get("messages")
                        .get(0)
                        .get("role")
                        .asString()
        ).isEqualTo("user");
        assertThat(payload.toString()).doesNotContain("server-token");
    }

    @Test
    @DisplayName("LLM 서버가 성공이 아닌 상태를 반환하면 스트림 실패로 변환한다")
    void start_failure_nonSuccessStatus() throws Exception {
        // given
        URI baseUri = startServer(
                exchange -> respond(
                        exchange,
                        401,
                        "unauthorized"
                )
        );
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                HttpClient.newHttpClient(),
                objectMapper,
                new LlmProperties(
                        baseUri,
                        "server-token",
                        "qwen/qwen3.6-27b",
                        512,
                        0.2,
                        Duration.ofSeconds(5)
                )
        );

        // when & then
        assertThatThrownBy(() -> client.start(new LlmRequest(List.of()))).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.LLM_STREAM_FAILED)
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
                "/v1/chat/completions",
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

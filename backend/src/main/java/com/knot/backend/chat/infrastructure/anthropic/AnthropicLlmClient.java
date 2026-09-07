package com.knot.backend.chat.infrastructure.anthropic;

import com.knot.backend.chat.application.LlmClient;
import com.knot.backend.chat.application.LlmStream;
import com.knot.backend.chat.application.dto.command.LlmRequest;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class AnthropicLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_ERROR_BODY_BYTES = 4096;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AnthropicLlmProperties properties;
    private final AnthropicRequestMapper requestMapper;

    public AnthropicLlmClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AnthropicLlmProperties properties
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.requestMapper = new AnthropicRequestMapper(properties);
    }

    @Override
    public LlmStream start(LlmRequest request) {
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(
                    request(request),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            throw new ChatException(
                    ChatErrorCode.LLM_STREAM_FAILED,
                    exception
            );
        } catch (HttpTimeoutException exception) {
            throw new ChatException(
                    ChatErrorCode.LLM_STREAM_TIMEOUT,
                    exception
            );
        } catch (IOException | JacksonException exception) {
            throw new ChatException(
                    ChatErrorCode.LLM_STREAM_FAILED,
                    exception
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failed(response);
        }
        return new AnthropicLlmStream(
                response.body(),
                objectMapper
        );
    }

    private HttpRequest request(LlmRequest request) throws JacksonException {
        return HttpRequest.newBuilder(properties.messagesUri())
                .timeout(properties.requestTimeout())
                .header(
                        "x-api-key",
                        properties.apiKey()
                )
                .header(
                        "anthropic-version",
                        ANTHROPIC_VERSION
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .header(
                        "Accept",
                        "text/event-stream"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(requestMapper.toPayload(request)),
                                StandardCharsets.UTF_8
                        )
                )
                .build();
    }

    // Retry-After는 SSE 응답 헤더가 이미 나간 뒤라 클라이언트에 실을 수 없어 로그로만 남긴다.
    private ChatException failed(HttpResponse<InputStream> response) {
        int status = response.statusCode();
        String errorType = errorType(response.body());
        String retryAfter = response.headers()
                .firstValue("retry-after")
                .orElse("-");
        log.warn(
                "Anthropic Messages API 요청 실패 status={} type={} retryAfter={}",
                status,
                errorType,
                retryAfter
        );
        return new ChatException(AnthropicErrorCodes.forStatus(status));
    }

    private String errorType(InputStream body) {
        try (body) {
            byte[] bytes = body.readNBytes(MAX_ERROR_BODY_BYTES);
            JsonNode error = objectMapper.readTree(bytes)
                    .path("error");
            return error.path("type")
                    .stringValue("unknown");
        } catch (IOException | JacksonException exception) {
            return "unknown";
        }
    }
}

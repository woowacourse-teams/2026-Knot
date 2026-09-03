package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.application.LlmClient;
import com.knot.backend.chat.application.LlmStream;
import com.knot.backend.chat.application.dto.command.LlmMessage;
import com.knot.backend.chat.application.dto.command.LlmRequest;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class OpenAiCompatibleLlmClient implements LlmClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LlmProperties properties;

    OpenAiCompatibleLlmClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            LlmProperties properties
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
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
            throw failed(exception);
        } catch (IOException | JacksonException exception) {
            throw failed(exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            close(response.body());
            throw failed();
        }
        return new OpenAiCompatibleLlmStream(
                response.body(),
                objectMapper
        );
    }

    private HttpRequest request(LlmRequest request) throws JacksonException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "model",
                properties.model()
        );
        payload.put(
                "messages",
                request.messages()
                        .stream()
                        .map(this::message)
                        .toList()
        );
        payload.put(
                "stream",
                true
        );
        payload.put(
                "max_tokens",
                properties.maxTokens()
        );
        payload.put(
                "temperature",
                properties.temperature()
        );
        payload.put(
                "reasoning_effort",
                properties.reasoningEffort()
        );
        return HttpRequest.newBuilder(properties.chatCompletionsUri())
                .timeout(properties.requestTimeout())
                .header(
                        "Authorization",
                        "Bearer " + properties.apiKey()
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
                                objectMapper.writeValueAsString(payload),
                                StandardCharsets.UTF_8
                        )
                )
                .build();
    }

    private Map<String, String> message(LlmMessage message) {
        return Map.of(
                "role",
                message.role()
                        .name()
                        .toLowerCase(Locale.ROOT),
                "content",
                message.content()
        );
    }

    private ChatException failed() {
        return new ChatException(ChatErrorCode.LLM_STREAM_FAILED);
    }

    private ChatException failed(Throwable cause) {
        return new ChatException(
                ChatErrorCode.LLM_STREAM_FAILED,
                cause
        );
    }

    private void close(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // 상태 코드 오류를 원인 없이 외부에 노출하지 않는다.
        }
    }
}

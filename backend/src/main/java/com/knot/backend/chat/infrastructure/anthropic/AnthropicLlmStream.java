package com.knot.backend.chat.infrastructure.anthropic;

import com.knot.backend.chat.application.LlmStream;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.LlmUsage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class AnthropicLlmStream implements LlmStream {
    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmStream.class);

    private final InputStream inputStream;
    private final BufferedReader reader;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean closed = new AtomicBoolean();
    private String nextChunk;
    private boolean messageStopped;
    private String model = "";
    private long inputTokens;
    private long cacheReadInputTokens;
    private long cacheCreationInputTokens;
    private long outputTokens;
    private boolean usageRecorded;

    AnthropicLlmStream(
            InputStream inputStream,
            ObjectMapper objectMapper
    ) {
        this.inputStream = inputStream;
        this.reader = new BufferedReader(
                new InputStreamReader(
                        inputStream,
                        StandardCharsets.UTF_8
                )
        );
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean hasNext() {
        if (nextChunk != null) {
            return true;
        }
        if (closed.get() || messageStopped) {
            return false;
        }
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length())
                        .trim();
                if (data.isEmpty()) {
                    continue;
                }
                JsonNode event = objectMapper.readTree(data);
                switch (event.path("type")
                        .stringValue("")) {
                    case "content_block_delta" -> {
                        String text = textDelta(event);
                        if (!text.isEmpty()) {
                            nextChunk = text;
                            return true;
                        }
                    }
                    case "message_start" -> recordInputUsage(event.path("message"));
                    case "message_delta" -> completeMessage(event);
                    case "message_stop" -> {
                        messageStopped = true;
                        closeInputStream();
                        return false;
                    }
                    case "error" -> throw streamError(event.path("error"));
                    default -> {
                        // ping, content_block_start/stop, thinking 델타는 chunk가 아니다.
                    }
                }
            }
            if (closed.get()) {
                return false;
            }
            closeInputStream();
            throw new ChatException(ChatErrorCode.LLM_STREAM_FAILED);
        } catch (JacksonException | IOException exception) {
            if (closed.get()) {
                return false;
            }
            closeInputStream();
            throw new ChatException(
                    ChatErrorCode.LLM_STREAM_FAILED,
                    exception
            );
        }
    }

    /** {@code message_start}를 못 받고 끝난 스트림(오류·즉시 종료)에는 사용량이 없다. */
    @Override
    public Optional<LlmUsage> usage() {
        if (!usageRecorded) {
            return Optional.empty();
        }
        return Optional.of(
                LlmUsage.of(
                        model,
                        inputTokens,
                        outputTokens,
                        cacheReadInputTokens,
                        cacheCreationInputTokens
                )
        );
    }

    @Override
    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException("LLM stream is complete");
        }
        String chunk = nextChunk;
        nextChunk = null;
        return chunk;
    }

    @Override
    public void close() {
        closeInputStream();
    }

    private String textDelta(JsonNode event) {
        JsonNode delta = event.path("delta");
        if (!"text_delta".equals(
                delta.path("type")
                        .stringValue("")
        )) {
            return "";
        }
        return delta.path("text")
                .stringValue("");
    }

    private void recordInputUsage(JsonNode message) {
        JsonNode usage = message.path("usage");
        model = message.path("model")
                .stringValue("");
        inputTokens = usage.path("input_tokens")
                .asLong(0);
        cacheReadInputTokens = usage.path("cache_read_input_tokens")
                .asLong(0);
        cacheCreationInputTokens = usage.path("cache_creation_input_tokens")
                .asLong(0);
        usageRecorded = true;
    }

    // 사용량은 로그로 남기고 usage()로도 올린다. 저장은 ChatMessageService가 답변과 같은 트랜잭션에 한다(B2).
    private void completeMessage(JsonNode event) {
        JsonNode delta = event.path("delta");
        outputTokens = event.path("usage")
                .path("output_tokens")
                .asLong(0);
        log.info(
                "Anthropic usage model={} inputTokens={} cacheReadInputTokens={} cacheCreationInputTokens={} outputTokens={}",
                model,
                inputTokens,
                cacheReadInputTokens,
                cacheCreationInputTokens,
                outputTokens
        );
        String stopReason = delta.path("stop_reason")
                .stringValue("");
        if ("refusal".equals(stopReason)) {
            JsonNode stopDetails = delta.path("stop_details")
                    .isMissingNode() ? event.path("stop_details") : delta.path("stop_details");
            log.warn(
                    "Anthropic 응답 거부 category={}",
                    stopDetails.path("category")
                            .stringValue("unknown")
            );
            closeInputStream();
            throw new ChatException(ChatErrorCode.LLM_REFUSED);
        }
        if ("max_tokens".equals(stopReason)) {
            log.warn(
                    "Anthropic 응답이 max_tokens에서 잘렸다 outputTokens={}",
                    outputTokens
            );
        }
    }

    private ChatException streamError(JsonNode error) {
        String errorType = error.path("type")
                .stringValue("unknown");
        log.warn(
                "Anthropic 스트림 오류 type={}",
                errorType
        );
        closeInputStream();
        return new ChatException(AnthropicErrorCodes.forErrorType(errorType));
    }

    private void closeInputStream() {
        if (!closed.compareAndSet(
                false,
                true
        )) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // 이미 종료된 외부 스트림은 추가로 전파하지 않는다.
        }
    }
}

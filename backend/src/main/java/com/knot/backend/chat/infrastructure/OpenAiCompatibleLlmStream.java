package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.application.LlmStream;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class OpenAiCompatibleLlmStream implements LlmStream {
    private final InputStream inputStream;
    private final BufferedReader reader;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean closed = new AtomicBoolean();
    private String nextChunk;
    private boolean doneReceived;
    private boolean visibleContentReceived;

    OpenAiCompatibleLlmStream(
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
        if (closed.get() || doneReceived) {
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
                if ("[DONE]".equals(data)) {
                    doneReceived = true;
                    if (!visibleContentReceived) {
                        throw new ChatException(ChatErrorCode.LLM_STREAM_FAILED);
                    }
                    closeInputStream();
                    return false;
                }
                String content = contentFrom(data);
                if (content != null && !content.isEmpty()) {
                    visibleContentReceived = true;
                    nextChunk = content;
                    return true;
                }
            }
            if (closed.get()) {
                return false;
            }
            throw new ChatException(ChatErrorCode.LLM_STREAM_FAILED);
        } catch (ChatException exception) {
            if (closed.get()) {
                return false;
            }
            closeInputStream();
            throw exception;
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

    private String contentFrom(String data) throws JacksonException {
        JsonNode response = objectMapper.readTree(data);
        if (response == null || !response.isObject()) {
            throw new ChatException(ChatErrorCode.LLM_STREAM_FAILED);
        }
        JsonNode error = response.get("error");
        if (error != null && !error.isNull()) {
            throw new ChatException(ChatErrorCode.LLM_STREAM_FAILED);
        }
        JsonNode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode choice = choices.get(0);
        JsonNode finishReason = choice.get("finish_reason");
        if (finishReason != null && "length".equals(finishReason.asString())) {
            throw new ChatException(ChatErrorCode.LLM_STREAM_FAILED);
        }
        JsonNode delta = choice.get("delta");
        if (delta == null) {
            return null;
        }
        JsonNode content = delta.get("content");
        return content != null && content.isString() ? content.asString() : null;
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

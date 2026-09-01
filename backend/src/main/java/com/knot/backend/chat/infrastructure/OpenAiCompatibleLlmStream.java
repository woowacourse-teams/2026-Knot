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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class OpenAiCompatibleLlmStream implements LlmStream {
    private final BufferedReader reader;
    private final ObjectMapper objectMapper;
    private String nextChunk;
    private boolean completed;
    private boolean closed;

    OpenAiCompatibleLlmStream(
            InputStream inputStream,
            ObjectMapper objectMapper
    ) {
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
        if (completed) {
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
                    complete();
                    return false;
                }
                String content = contentFrom(data);
                if (content != null && !content.isEmpty()) {
                    nextChunk = content;
                    return true;
                }
            }
            complete();
            return false;
        } catch (JacksonException | IOException exception) {
            complete();
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
        complete();
    }

    private String contentFrom(String data) throws JacksonException {
        JsonNode response = objectMapper.readTree(data);
        JsonNode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode delta = choices.get(0)
                .get("delta");
        if (delta == null) {
            return null;
        }
        JsonNode content = delta.get("content");
        return content != null && content.isString() ? content.asString() : null;
    }

    private void complete() {
        if (completed && closed) {
            return;
        }
        completed = true;
        if (closed) {
            return;
        }
        closed = true;
        try {
            reader.close();
        } catch (IOException ignored) {
            // 이미 종료된 외부 스트림은 추가로 전파하지 않는다.
        }
    }
}

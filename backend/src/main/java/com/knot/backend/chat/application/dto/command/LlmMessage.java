package com.knot.backend.chat.application.dto.command;

public record LlmMessage(
        LlmMessageRole role,
        String content
) {
}

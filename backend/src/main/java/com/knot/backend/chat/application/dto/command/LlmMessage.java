package com.knot.backend.chat.application.dto.command;

import com.knot.backend.chat.domain.ChatMessageRole;

public record LlmMessage(
        ChatMessageRole role,
        String content
) {
}

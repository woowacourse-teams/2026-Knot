package com.knot.backend.chat.presentation.dto.response;

import com.knot.backend.chat.application.dto.result.ChatSessionResult;
import java.time.Instant;

public record ChatSessionResponse(
        long id,
        String title,
        Instant createdAt,
        Instant lastMessageAt
) {

    public static ChatSessionResponse from(ChatSessionResult result) {
        return new ChatSessionResponse(
                result.id(),
                result.title(),
                result.createdAt(),
                result.lastMessageAt()
        );
    }
}

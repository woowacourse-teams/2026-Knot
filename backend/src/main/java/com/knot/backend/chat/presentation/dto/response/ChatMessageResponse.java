package com.knot.backend.chat.presentation.dto.response;

import com.knot.backend.chat.application.dto.result.ChatMessageResult;
import com.knot.backend.chat.domain.ChatMessageRole;
import java.time.Instant;

public record ChatMessageResponse(
        long id,
        ChatMessageRole role,
        String content,
        Instant createdAt
) {

    public static ChatMessageResponse from(ChatMessageResult result) {
        return new ChatMessageResponse(
                result.id(),
                result.role(),
                result.content(),
                result.createdAt()
        );
    }
}

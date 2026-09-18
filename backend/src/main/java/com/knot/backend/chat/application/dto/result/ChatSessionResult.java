package com.knot.backend.chat.application.dto.result;

import com.knot.backend.chat.domain.ChatSession;
import java.time.Instant;

public record ChatSessionResult(
        long id,
        String title,
        Instant createdAt,
        Instant lastMessageAt
) {

    public static ChatSessionResult from(ChatSession chatSession) {
        return new ChatSessionResult(
                chatSession.getId(),
                chatSession.getTitle(),
                chatSession.getCreatedAt(),
                chatSession.getLastMessageAt()
        );
    }
}

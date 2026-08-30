package com.knot.backend.chat.application.dto.result;

import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRole;
import java.time.Instant;

public record ChatMessageResult(
        long id,
        ChatMessageRole role,
        String content,
        Instant createdAt
) {

    public static ChatMessageResult from(ChatMessage chatMessage) {
        return new ChatMessageResult(
                chatMessage.getId(),
                chatMessage.getRole(),
                chatMessage.getContent(),
                chatMessage.getCreatedAt()
        );
    }
}

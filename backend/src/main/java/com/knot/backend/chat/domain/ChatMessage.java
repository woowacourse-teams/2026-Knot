package com.knot.backend.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {}

    private ChatMessage(
            Long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt
    ) {
        validateSessionId(sessionId);
        validateRole(role);
        validateContent(content);
        validateCreatedAt(createdAt);
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static ChatMessage create(
            Long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt
    ) {
        return new ChatMessage(
                sessionId,
                role,
                content,
                createdAt
        );
    }

    private static void validateSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_MESSAGE_SESSION_ID);
        }
    }

    private static void validateRole(ChatMessageRole role) {
        if (role == null) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_MESSAGE_ROLE);
        }
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_MESSAGE_CONTENT);
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_MESSAGE_CREATED_AT);
        }
    }
}

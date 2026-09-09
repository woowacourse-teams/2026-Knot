package com.knot.backend.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by", nullable = false, length = 10)
    private ChatMessageGeneratedBy generatedBy;

    /** 서버가 모델을 불러 만든 답변에만 있다(로드맵 B2). 그 밖의 메시지는 null이고 컬럼도 전부 NULL이다. */
    @Embedded
    private LlmUsage usage;

    protected ChatMessage() {}

    private ChatMessage(
            Long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt,
            ChatMessageGeneratedBy generatedBy,
            LlmUsage usage
    ) {
        validateSessionId(sessionId);
        validateRole(role);
        validateContent(content);
        validateCreatedAt(createdAt);
        validateGeneratedBy(generatedBy);
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.generatedBy = generatedBy;
        this.usage = usage;
    }

    public static ChatMessage create(
            Long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt
    ) {
        return create(
                sessionId,
                role,
                content,
                createdAt,
                ChatMessageGeneratedBy.SERVER
        );
    }

    /** 서버가 아닌 클라이언트(CLI 에이전트·데스크톱 구독 호출)가 만든 답변은 {@code generatedBy=CLIENT}로 구분한다(데스크톱 로드맵 Q25). */
    public static ChatMessage create(
            Long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt,
            ChatMessageGeneratedBy generatedBy
    ) {
        return new ChatMessage(
                sessionId,
                role,
                content,
                createdAt,
                generatedBy,
                null
        );
    }

    /** 서버가 모델을 불러 만든 답변. 사용량을 답변과 같은 행에 남긴다(데스크톱 기획서 6.2 계측 행, 로드맵 B2). */
    public static ChatMessage createServerAnswer(
            Long sessionId,
            String content,
            Instant createdAt,
            LlmUsage usage
    ) {
        return new ChatMessage(
                sessionId,
                ChatMessageRole.ASSISTANT,
                content,
                createdAt,
                ChatMessageGeneratedBy.SERVER,
                usage
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

    private static void validateGeneratedBy(ChatMessageGeneratedBy generatedBy) {
        if (generatedBy == null) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_MESSAGE_GENERATED_BY);
        }
    }
}

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
@Table(name = "chat_feedback")
public class ChatFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatFeedbackResult result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatFeedback() {}

    private ChatFeedback(
            Long messageId,
            Long memberId,
            ChatFeedbackResult result,
            Instant createdAt
    ) {
        validateMessageId(messageId);
        validateMemberId(memberId);
        validateResult(result);
        validateCreatedAt(createdAt);
        this.messageId = messageId;
        this.memberId = memberId;
        this.result = result;
        this.createdAt = createdAt;
    }

    public static ChatFeedback create(
            Long messageId,
            Long memberId,
            ChatFeedbackResult result,
            Instant createdAt
    ) {
        return new ChatFeedback(
                messageId,
                memberId,
                result,
                createdAt
        );
    }

    public void changeResult(ChatFeedbackResult result) {
        validateResult(result);
        this.result = result;
    }

    private static void validateMessageId(Long messageId) {
        if (messageId == null || messageId <= 0) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_FEEDBACK_MESSAGE_ID);
        }
    }

    private static void validateMemberId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_FEEDBACK_MEMBER_ID);
        }
    }

    private static void validateResult(ChatFeedbackResult result) {
        if (result == null) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_FEEDBACK_RESULT);
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_FEEDBACK_CREATED_AT);
        }
    }
}

package com.knot.backend.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    public static final String DEFAULT_TITLE = "새 대화";
    public static final int MAX_TITLE_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    protected ChatSession() {}

    private ChatSession(
            Long workspaceId,
            Long memberId,
            String title,
            Instant createdAt
    ) {
        validateWorkspaceId(workspaceId);
        validateMemberId(memberId);
        validateTitle(title);
        validateCreatedAt(createdAt);
        this.workspaceId = workspaceId;
        this.memberId = memberId;
        this.title = resolveTitle(title);
        this.createdAt = createdAt;
        this.lastMessageAt = createdAt;
    }

    public static ChatSession create(
            Long workspaceId,
            Long memberId,
            String title,
            Instant createdAt
    ) {
        return new ChatSession(
                workspaceId,
                memberId,
                title,
                createdAt
        );
    }

    public void updateLastMessageAt(Instant messageCreatedAt) {
        if (messageCreatedAt == null) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_SESSION_LAST_MESSAGE_AT);
        }
        if (messageCreatedAt.isAfter(lastMessageAt)) {
            this.lastMessageAt = messageCreatedAt;
        }
    }

    private static String resolveTitle(String title) {
        return title == null || title.isBlank() ? DEFAULT_TITLE : title;
    }

    private static void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_SESSION_WORKSPACE_ID);
        }
    }

    private static void validateMemberId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_SESSION_MEMBER_ID);
        }
    }

    private static void validateTitle(String title) {
        if (title != null && !title.isBlank() && title.length() > MAX_TITLE_LENGTH) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_SESSION_TITLE);
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_SESSION_CREATED_AT);
        }
    }
}

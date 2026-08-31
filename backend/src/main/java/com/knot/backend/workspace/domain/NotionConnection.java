package com.knot.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

@Getter
@Entity
@Table(name = "notion_connections")
public class NotionConnection {
    private static final String USER_OWNER_TYPE = "user";
    private static final String WORKSPACE_OWNER_TYPE = "workspace";
    public static final int MAX_SECRET_ENVELOPE_LENGTH = 1_024;
    public static final int MAX_NOTION_ID_LENGTH = 255;
    public static final int MAX_NOTION_WORKSPACE_NAME_LENGTH = 255;
    public static final int MAX_NOTION_WORKSPACE_ICON_LENGTH = 2_048;
    public static final int MAX_NOTION_OWNER_TYPE_LENGTH = 32;
    public static final int MAX_NOTION_REQUEST_ID_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "access_token_ciphertext", nullable = false, length = MAX_SECRET_ENVELOPE_LENGTH)
    private String accessTokenCiphertext;

    @Column(name = "refresh_token_ciphertext", length = MAX_SECRET_ENVELOPE_LENGTH)
    private String refreshTokenCiphertext;

    @Column(name = "notion_workspace_id", nullable = false, length = MAX_NOTION_ID_LENGTH)
    private String notionWorkspaceId;

    @Column(name = "notion_workspace_name", length = MAX_NOTION_WORKSPACE_NAME_LENGTH)
    private String notionWorkspaceName;

    @Column(name = "notion_workspace_icon", length = MAX_NOTION_WORKSPACE_ICON_LENGTH)
    private String notionWorkspaceIcon;

    @Column(name = "bot_id", nullable = false, length = MAX_NOTION_ID_LENGTH)
    private String botId;

    @Column(name = "owner_type", nullable = false, length = MAX_NOTION_OWNER_TYPE_LENGTH)
    private String ownerType;

    @Column(name = "owner_user_id", length = MAX_NOTION_ID_LENGTH)
    private String ownerUserId;

    @Column(name = "duplicated_template_id", length = MAX_NOTION_ID_LENGTH)
    private String duplicatedTemplateId;

    @Column(name = "request_id", length = MAX_NOTION_REQUEST_ID_LENGTH)
    private String requestId;

    @Column(name = "authorizing_member_id", nullable = false)
    private Long authorizingMemberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected NotionConnection() {}

    private NotionConnection(
            Long workspaceId,
            String accessTokenCiphertext,
            String refreshTokenCiphertext,
            String notionWorkspaceId,
            String notionWorkspaceName,
            String notionWorkspaceIcon,
            String botId,
            String ownerType,
            String ownerUserId,
            String duplicatedTemplateId,
            String requestId,
            Long authorizingMemberId,
            Instant createdAt
    ) {
        validateWorkspaceId(workspaceId);
        validateConnectionValues(
                accessTokenCiphertext,
                refreshTokenCiphertext,
                notionWorkspaceId,
                notionWorkspaceName,
                notionWorkspaceIcon,
                botId,
                ownerType,
                ownerUserId,
                duplicatedTemplateId,
                requestId,
                authorizingMemberId
        );
        validateUpdatedAt(createdAt);
        this.workspaceId = workspaceId;
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.refreshTokenCiphertext = refreshTokenCiphertext;
        this.notionWorkspaceId = notionWorkspaceId;
        this.notionWorkspaceName = notionWorkspaceName;
        this.notionWorkspaceIcon = notionWorkspaceIcon;
        this.botId = botId;
        this.ownerType = ownerType;
        this.ownerUserId = ownerUserId;
        this.duplicatedTemplateId = duplicatedTemplateId;
        this.requestId = requestId;
        this.authorizingMemberId = authorizingMemberId;
        this.createdAt = truncateToDatabasePrecision(createdAt);
        this.updatedAt = truncateToDatabasePrecision(createdAt);
    }

    public static NotionConnection create(
            Long workspaceId,
            String accessTokenCiphertext,
            String refreshTokenCiphertext,
            String notionWorkspaceId,
            String notionWorkspaceName,
            String notionWorkspaceIcon,
            String botId,
            String ownerType,
            String ownerUserId,
            String duplicatedTemplateId,
            String requestId,
            Long authorizingMemberId,
            Instant createdAt
    ) {
        return new NotionConnection(
                workspaceId,
                accessTokenCiphertext,
                refreshTokenCiphertext,
                notionWorkspaceId,
                notionWorkspaceName,
                notionWorkspaceIcon,
                botId,
                ownerType,
                ownerUserId,
                duplicatedTemplateId,
                requestId,
                authorizingMemberId,
                createdAt
        );
    }

    public void replace(
            String accessTokenCiphertext,
            String refreshTokenCiphertext,
            String notionWorkspaceId,
            String notionWorkspaceName,
            String notionWorkspaceIcon,
            String botId,
            String ownerType,
            String ownerUserId,
            String duplicatedTemplateId,
            String requestId,
            Long authorizingMemberId,
            Instant updatedAt
    ) {
        validateConnectionValues(
                accessTokenCiphertext,
                refreshTokenCiphertext,
                notionWorkspaceId,
                notionWorkspaceName,
                notionWorkspaceIcon,
                botId,
                ownerType,
                ownerUserId,
                duplicatedTemplateId,
                requestId,
                authorizingMemberId
        );
        validateUpdatedAt(updatedAt);
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.refreshTokenCiphertext = refreshTokenCiphertext;
        this.notionWorkspaceId = notionWorkspaceId;
        this.notionWorkspaceName = notionWorkspaceName;
        this.notionWorkspaceIcon = notionWorkspaceIcon;
        this.botId = botId;
        this.ownerType = ownerType;
        this.ownerUserId = ownerUserId;
        this.duplicatedTemplateId = duplicatedTemplateId;
        this.requestId = requestId;
        this.authorizingMemberId = authorizingMemberId;
        this.updatedAt = truncateToDatabasePrecision(updatedAt);
    }

    private void validateConnectionValues(
            String accessTokenCiphertext,
            String refreshTokenCiphertext,
            String notionWorkspaceId,
            String notionWorkspaceName,
            String notionWorkspaceIcon,
            String botId,
            String ownerType,
            String ownerUserId,
            String duplicatedTemplateId,
            String requestId,
            Long authorizingMemberId
    ) {
        validateRequiredText(
                accessTokenCiphertext,
                MAX_SECRET_ENVELOPE_LENGTH
        );
        validateOptionalText(
                refreshTokenCiphertext,
                MAX_SECRET_ENVELOPE_LENGTH
        );
        validateRequiredText(
                notionWorkspaceId,
                MAX_NOTION_ID_LENGTH
        );
        validateOptionalText(
                notionWorkspaceName,
                MAX_NOTION_WORKSPACE_NAME_LENGTH
        );
        validateOptionalText(
                notionWorkspaceIcon,
                MAX_NOTION_WORKSPACE_ICON_LENGTH
        );
        validateRequiredText(
                botId,
                MAX_NOTION_ID_LENGTH
        );
        validateOwner(
                ownerType,
                ownerUserId
        );
        validateOptionalText(
                duplicatedTemplateId,
                MAX_NOTION_ID_LENGTH
        );
        validateOptionalText(
                requestId,
                MAX_NOTION_REQUEST_ID_LENGTH
        );
        validateAuthorizingMemberId(authorizingMemberId);
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new NotionException(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
        }
    }

    private void validateRequiredText(
            String value,
            int maxLength
    ) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new NotionException(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED);
        }
    }

    private void validateOptionalText(
            String value,
            int maxLength
    ) {
        if (value != null && (value.isBlank() || value.length() > maxLength)) {
            throw new NotionException(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED);
        }
    }

    private void validateAuthorizingMemberId(Long authorizingMemberId) {
        if (authorizingMemberId == null || authorizingMemberId <= 0) {
            throw new NotionException(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
        }
    }

    private void validateOwner(
            String ownerType,
            String ownerUserId
    ) {
        validateRequiredText(
                ownerType,
                MAX_NOTION_OWNER_TYPE_LENGTH
        );
        validateOptionalText(
                ownerUserId,
                MAX_NOTION_ID_LENGTH
        );
        boolean validUserOwner = USER_OWNER_TYPE.equals(ownerType) && ownerUserId != null;
        boolean validWorkspaceOwner = WORKSPACE_OWNER_TYPE.equals(ownerType) && ownerUserId == null;
        if (!validUserOwner && !validWorkspaceOwner) {
            throw new NotionException(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED);
        }
    }

    private void validateUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new NotionException(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
        }
    }

    private Instant truncateToDatabasePrecision(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}

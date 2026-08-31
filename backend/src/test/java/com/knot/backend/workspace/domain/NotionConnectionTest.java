package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotionConnectionTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final Long AUTHORIZING_MEMBER_ID = 2L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-31T01:00:00Z");
    private static final Instant UPDATED_AT_WITH_NANOS = Instant.parse("2026-08-31T01:00:00.123456789Z");

    @DisplayName("암호화된 토큰과 Notion 식별자로 Connection을 생성한다")
    @Test
    void create_success() {
        // given
        Instant expectedCreatedAt = CREATED_AT.truncatedTo(ChronoUnit.MICROS);

        // when
        NotionConnection connection = createConnection();

        // then
        assertThat(connection.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(connection.getAccessTokenCiphertext()).isEqualTo("access-envelope");
        assertThat(connection.getRefreshTokenCiphertext()).isEqualTo("refresh-envelope");
        assertThat(connection.getNotionWorkspaceId()).isEqualTo("notion-workspace-id");
        assertThat(connection.getNotionWorkspaceName()).isEqualTo("Knot Notion");
        assertThat(connection.getNotionWorkspaceIcon()).isEqualTo("https://static.notion.test/icon.png");
        assertThat(connection.getBotId()).isEqualTo("bot-id");
        assertThat(connection.getOwnerType()).isEqualTo("user");
        assertThat(connection.getOwnerUserId()).isEqualTo("notion-owner-user-id");
        assertThat(connection.getDuplicatedTemplateId()).isEqualTo("template-id");
        assertThat(connection.getRequestId()).isEqualTo("request-id");
        assertThat(connection.getAuthorizingMemberId()).isEqualTo(AUTHORIZING_MEMBER_ID);
        assertThat(connection.getCreatedAt()).isEqualTo(expectedCreatedAt);
        assertThat(connection.getUpdatedAt()).isEqualTo(expectedCreatedAt);
    }

    @DisplayName("OAuth 재연결로 Connection의 토큰과 Notion 식별자를 교체한다")
    @Test
    void replace_success() {
        // given
        NotionConnection connection = createConnection();
        Instant expectedUpdatedAt = UPDATED_AT_WITH_NANOS.truncatedTo(ChronoUnit.MICROS);

        // when
        connection.replace(
                "new-access-envelope",
                null,
                "new-notion-workspace-id",
                null,
                null,
                "new-bot-id",
                "workspace",
                null,
                null,
                "new-request-id",
                3L,
                UPDATED_AT_WITH_NANOS
        );

        // then
        assertThat(connection.getAccessTokenCiphertext()).isEqualTo("new-access-envelope");
        assertThat(connection.getRefreshTokenCiphertext()).isNull();
        assertThat(connection.getNotionWorkspaceId()).isEqualTo("new-notion-workspace-id");
        assertThat(connection.getNotionWorkspaceName()).isNull();
        assertThat(connection.getNotionWorkspaceIcon()).isNull();
        assertThat(connection.getBotId()).isEqualTo("new-bot-id");
        assertThat(connection.getOwnerType()).isEqualTo("workspace");
        assertThat(connection.getOwnerUserId()).isNull();
        assertThat(connection.getDuplicatedTemplateId()).isNull();
        assertThat(connection.getRequestId()).isEqualTo("new-request-id");
        assertThat(connection.getAuthorizingMemberId()).isEqualTo(3L);
        assertThat(connection.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(connection.getUpdatedAt()).isEqualTo(expectedUpdatedAt);
    }

    @DisplayName("access token 암호문이 비어 있으면 Connection 생성을 거부한다")
    @Test
    void create_failure_blankAccessTokenCiphertext() {
        // given
        String blankAccessTokenCiphertext = " ";

        // when
        ThrowingCallable action = () -> NotionConnection.create(
                WORKSPACE_ID,
                blankAccessTokenCiphertext,
                "refresh-envelope",
                "notion-workspace-id",
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                "user",
                "notion-owner-user-id",
                "template-id",
                "request-id",
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED);
    }

    @DisplayName("refresh token 암호문은 없을 수 있지만 비어 있을 수는 없다")
    @Test
    void create_failure_blankRefreshTokenCiphertext() {
        // given
        String blankRefreshTokenCiphertext = " ";

        // when
        ThrowingCallable action = () -> NotionConnection.create(
                WORKSPACE_ID,
                "access-envelope",
                blankRefreshTokenCiphertext,
                "notion-workspace-id",
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                "user",
                "notion-owner-user-id",
                "template-id",
                "request-id",
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED);
    }

    @DisplayName("user owner에 Notion user ID가 없으면 Connection 생성을 거부한다")
    @Test
    void create_failure_missingUserOwnerId() {
        // given
        String missingOwnerUserId = null;

        // when
        ThrowingCallable action = () -> NotionConnection.create(
                WORKSPACE_ID,
                "access-envelope",
                null,
                "notion-workspace-id",
                "Knot Notion",
                null,
                "bot-id",
                "user",
                missingOwnerUserId,
                null,
                null,
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED);
    }

    @DisplayName("workspace owner에 Notion user ID가 있으면 Connection 생성을 거부한다")
    @Test
    void create_failure_workspaceOwnerWithUserId() {
        // given
        String unexpectedOwnerUserId = "notion-owner-user-id";

        // when
        ThrowingCallable action = () -> NotionConnection.create(
                WORKSPACE_ID,
                "access-envelope",
                null,
                "notion-workspace-id",
                "Knot Notion",
                null,
                "bot-id",
                "workspace",
                unexpectedOwnerUserId,
                null,
                null,
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED);
    }

    @DisplayName("알 수 없는 owner type이면 Connection 생성을 거부한다")
    @Test
    void create_failure_unknownOwnerType() {
        // given
        String unknownOwnerType = "team";

        // when
        ThrowingCallable action = () -> NotionConnection.create(
                WORKSPACE_ID,
                "access-envelope",
                null,
                "notion-workspace-id",
                "Knot Notion",
                null,
                "bot-id",
                unknownOwnerType,
                null,
                null,
                null,
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED);
    }

    @DisplayName("수정 시각이 없으면 Connection 교체를 거부한다")
    @Test
    void replace_failure_missingUpdatedAt() {
        // given
        NotionConnection connection = createConnection();
        Instant missingUpdatedAt = null;

        // when
        ThrowingCallable action = () -> connection.replace(
                "new-access-envelope",
                null,
                "new-notion-workspace-id",
                null,
                null,
                "new-bot-id",
                "workspace",
                null,
                null,
                null,
                3L,
                missingUpdatedAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
    }

    private NotionConnection createConnection() {
        return NotionConnection.create(
                WORKSPACE_ID,
                "access-envelope",
                "refresh-envelope",
                "notion-workspace-id",
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                "user",
                "notion-owner-user-id",
                "template-id",
                "request-id",
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );
    }
}

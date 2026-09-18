package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentSourceConnectionTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final Long AUTHORIZING_MEMBER_ID = 2L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-31T01:00:00Z");
    private static final Instant UPDATED_AT_WITH_NANOS = Instant.parse("2026-08-31T01:00:00.123456789Z");

    @DisplayName("암호화된 인증 정보와 외부 소스 식별자로 Connection을 생성한다")
    @Test
    void create_success() {
        // given
        Instant expectedCreatedAt = CREATED_AT.truncatedTo(ChronoUnit.MICROS);

        // when
        ContentSourceConnection connection = createConnection();

        // then
        assertThat(connection.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(connection.getAccessCredentialCiphertext()).isEqualTo("access-envelope");
        assertThat(connection.getRefreshCredentialCiphertext()).isEqualTo("refresh-envelope");
        assertThat(connection.getExternalSourceId()).isEqualTo("notion-workspace-id");
        assertThat(connection.getExternalSourceName()).isEqualTo("Knot Notion");
        assertThat(connection.getExternalSourceIcon()).isEqualTo("https://static.notion.test/icon.png");
        assertThat(connection.getProviderConnectionId()).isEqualTo("bot-id");
        assertThat(connection.getAuthorizationOwnerType()).isEqualTo(ContentSourceAuthorizationOwnerType.USER);
        assertThat(connection.getAuthorizationOwnerId()).isEqualTo("notion-owner-user-id");
        assertThat(connection.getExternalTemplateId()).isEqualTo("template-id");
        assertThat(connection.getProviderRequestId()).isEqualTo("request-id");
        assertThat(connection.getAuthorizingMemberId()).isEqualTo(AUTHORIZING_MEMBER_ID);
        assertThat(connection.getCreatedAt()).isEqualTo(expectedCreatedAt);
        assertThat(connection.getUpdatedAt()).isEqualTo(expectedCreatedAt);
    }

    @DisplayName("OAuth 재연결로 Connection의 인증 정보와 외부 소스 식별자를 교체한다")
    @Test
    void replace_success() {
        // given
        ContentSourceConnection connection = createConnection();
        Instant expectedUpdatedAt = UPDATED_AT_WITH_NANOS.truncatedTo(ChronoUnit.MICROS);

        // when
        connection.replace(
                ContentSourceProvider.NOTION,
                "new-access-envelope",
                null,
                "new-notion-workspace-id",
                null,
                null,
                "new-bot-id",
                ContentSourceAuthorizationOwnerType.WORKSPACE,
                null,
                null,
                "new-request-id",
                3L,
                UPDATED_AT_WITH_NANOS
        );

        // then
        assertThat(connection.getAccessCredentialCiphertext()).isEqualTo("new-access-envelope");
        assertThat(connection.getRefreshCredentialCiphertext()).isNull();
        assertThat(connection.getExternalSourceId()).isEqualTo("new-notion-workspace-id");
        assertThat(connection.getExternalSourceName()).isNull();
        assertThat(connection.getExternalSourceIcon()).isNull();
        assertThat(connection.getProviderConnectionId()).isEqualTo("new-bot-id");
        assertThat(connection.getAuthorizationOwnerType()).isEqualTo(ContentSourceAuthorizationOwnerType.WORKSPACE);
        assertThat(connection.getAuthorizationOwnerId()).isNull();
        assertThat(connection.getExternalTemplateId()).isNull();
        assertThat(connection.getProviderRequestId()).isEqualTo("new-request-id");
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
        ThrowingCallable action = () -> ContentSourceConnection.create(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                blankAccessTokenCiphertext,
                "refresh-envelope",
                "notion-workspace-id",
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                ContentSourceAuthorizationOwnerType.USER,
                "notion-owner-user-id",
                "template-id",
                "request-id",
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ContentSourceException.class)
                .extracting(exception -> ((ContentSourceException) exception).getErrorCode())
                .isEqualTo(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_CONNECTION);
    }

    @DisplayName("refresh token 암호문은 없을 수 있지만 비어 있을 수는 없다")
    @Test
    void create_failure_blankRefreshTokenCiphertext() {
        // given
        String blankRefreshTokenCiphertext = " ";

        // when
        ThrowingCallable action = () -> ContentSourceConnection.create(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                "access-envelope",
                blankRefreshTokenCiphertext,
                "notion-workspace-id",
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                ContentSourceAuthorizationOwnerType.USER,
                "notion-owner-user-id",
                "template-id",
                "request-id",
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ContentSourceException.class)
                .extracting(exception -> ((ContentSourceException) exception).getErrorCode())
                .isEqualTo(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_CONNECTION);
    }

    @DisplayName("user owner에 외부 owner ID가 없으면 Connection 생성을 거부한다")
    @Test
    void create_failure_missingUserOwnerId() {
        // given
        String missingOwnerUserId = null;

        // when
        ThrowingCallable action = () -> ContentSourceConnection.create(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                "access-envelope",
                null,
                "notion-workspace-id",
                "Knot Notion",
                null,
                "bot-id",
                ContentSourceAuthorizationOwnerType.USER,
                missingOwnerUserId,
                null,
                null,
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ContentSourceException.class)
                .extracting(exception -> ((ContentSourceException) exception).getErrorCode())
                .isEqualTo(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_CONNECTION);
    }

    @DisplayName("workspace owner에 외부 owner ID가 있으면 Connection 생성을 거부한다")
    @Test
    void create_failure_workspaceOwnerWithUserId() {
        // given
        String unexpectedOwnerUserId = "notion-owner-user-id";

        // when
        ThrowingCallable action = () -> ContentSourceConnection.create(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                "access-envelope",
                null,
                "notion-workspace-id",
                "Knot Notion",
                null,
                "bot-id",
                ContentSourceAuthorizationOwnerType.WORKSPACE,
                unexpectedOwnerUserId,
                null,
                null,
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ContentSourceException.class)
                .extracting(exception -> ((ContentSourceException) exception).getErrorCode())
                .isEqualTo(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_CONNECTION);
    }

    @DisplayName("수정 시각이 없으면 Connection 교체를 거부한다")
    @Test
    void replace_failure_missingUpdatedAt() {
        // given
        ContentSourceConnection connection = createConnection();
        Instant missingUpdatedAt = null;

        // when
        ThrowingCallable action = () -> connection.replace(
                ContentSourceProvider.NOTION,
                "new-access-envelope",
                null,
                "new-notion-workspace-id",
                null,
                null,
                "new-bot-id",
                ContentSourceAuthorizationOwnerType.WORKSPACE,
                null,
                null,
                null,
                3L,
                missingUpdatedAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ContentSourceException.class)
                .extracting(exception -> ((ContentSourceException) exception).getErrorCode())
                .isEqualTo(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_CONNECTION);
    }

    private ContentSourceConnection createConnection() {
        return ContentSourceConnection.create(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                "access-envelope",
                "refresh-envelope",
                "notion-workspace-id",
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                ContentSourceAuthorizationOwnerType.USER,
                "notion-owner-user-id",
                "template-id",
                "request-id",
                AUTHORIZING_MEMBER_ID,
                CREATED_AT
        );
    }
}

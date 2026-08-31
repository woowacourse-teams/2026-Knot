package com.knot.backend.notion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.notion.application.dto.result.NotionOAuthAuthorizationContext;
import com.knot.backend.notion.application.dto.result.NotionOAuthToken;
import com.knot.backend.notion.domain.NotionConnection;
import com.knot.backend.notion.domain.NotionConnectionRepository;
import com.knot.backend.notion.domain.NotionErrorCode;
import com.knot.backend.notion.domain.NotionException;
import com.knot.backend.notion.domain.NotionOAuthAuthorizationRepository;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotionConnectionServiceTest {
    private static final Long AUTHORIZATION_ID = 7L;
    private static final Long WORKSPACE_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final URI CALLBACK_URI = URI.create("https://api.knot.test/api/v1/notion/oauth/callback");
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00.123456789Z");
    private static final Instant CURRENT_TIME = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String ACCESS_TOKEN_CIPHERTEXT = "access-envelope";
    private static final String REFRESH_TOKEN_CIPHERTEXT = "refresh-envelope";

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final NotionOAuthAuthorizationRepository authorizationRepository = mock(
            NotionOAuthAuthorizationRepository.class
    );
    private final NotionConnectionRepository connectionRepository = mock(NotionConnectionRepository.class);
    private final NotionOAuthSecretProtector secretProtector = mock(NotionOAuthSecretProtector.class);
    private final NotionConnectionService service = new NotionConnectionService(
            workspaceRepository,
            workspaceMemberRepository,
            authorizationRepository,
            connectionRepository,
            secretProtector,
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            )
    );

    @DisplayName("OAuth 토큰을 암호화해 새 Notion connection을 저장한다")
    @Test
    void connect_success_createsConnectionWithEncryptedTokens() {
        // given
        allowCurrentOwnerAuthorization();
        when(connectionRepository.findByWorkspaceIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.empty());
        whenEncryptsTokens();

        // when
        service.connect(
                authorization(),
                token()
        );

        // then
        ArgumentCaptor<NotionConnection> connectionCaptor = ArgumentCaptor.forClass(NotionConnection.class);
        verify(connectionRepository).save(connectionCaptor.capture());
        assertThat(connectionCaptor.getValue()).extracting(
                NotionConnection::getWorkspaceId,
                NotionConnection::getAccessTokenCiphertext,
                NotionConnection::getRefreshTokenCiphertext,
                NotionConnection::getNotionWorkspaceId,
                NotionConnection::getNotionWorkspaceName,
                NotionConnection::getNotionWorkspaceIcon,
                NotionConnection::getBotId,
                NotionConnection::getOwnerType,
                NotionConnection::getOwnerUserId,
                NotionConnection::getDuplicatedTemplateId,
                NotionConnection::getRequestId,
                NotionConnection::getAuthorizingMemberId,
                NotionConnection::getCreatedAt,
                NotionConnection::getUpdatedAt
        )
                .containsExactly(
                        WORKSPACE_ID,
                        ACCESS_TOKEN_CIPHERTEXT,
                        REFRESH_TOKEN_CIPHERTEXT,
                        "notion-workspace-id",
                        "Knot Notion",
                        "https://static.notion.test/icon.png",
                        "bot-id",
                        "user",
                        "notion-owner-user-id",
                        "template-id",
                        "request-id",
                        MEMBER_ID,
                        CURRENT_TIME,
                        CURRENT_TIME
                );
    }

    @DisplayName("refresh token이 없으면 access token만 암호화해 저장한다")
    @Test
    void connect_success_withoutRefreshToken() {
        // given
        allowCurrentOwnerAuthorization();
        when(connectionRepository.findByWorkspaceIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(
                secretProtector.encrypt(
                        WORKSPACE_ID,
                        NotionOAuthCredentialKind.ACCESS_TOKEN,
                        ACCESS_TOKEN
                )
        ).thenReturn(ACCESS_TOKEN_CIPHERTEXT);
        NotionOAuthToken token = new NotionOAuthToken(
                ACCESS_TOKEN,
                null,
                "notion-workspace-id",
                "Knot Notion",
                null,
                "bot-id",
                "user",
                "notion-owner-user-id",
                null,
                null
        );

        // when
        service.connect(
                authorization(),
                token
        );

        // then
        ArgumentCaptor<NotionConnection> connectionCaptor = ArgumentCaptor.forClass(NotionConnection.class);
        verify(connectionRepository).save(connectionCaptor.capture());
        assertThat(
                connectionCaptor.getValue()
                        .getRefreshTokenCiphertext()
        ).isNull();
        verify(
                secretProtector,
                never()
        ).encrypt(
                WORKSPACE_ID,
                NotionOAuthCredentialKind.REFRESH_TOKEN,
                null
        );
    }

    @DisplayName("이미 연결된 워크스페이스는 새 Notion connection 값으로 교체한다")
    @Test
    void connect_success_replacesExistingConnection() {
        // given
        allowCurrentOwnerAuthorization();
        NotionConnection existingConnection = NotionConnection.create(
                WORKSPACE_ID,
                "old-access-envelope",
                "old-refresh-envelope",
                "old-notion-workspace-id",
                "Old Notion",
                null,
                "old-bot-id",
                "user",
                "old-notion-owner-user-id",
                null,
                null,
                3L,
                CURRENT_TIME.minusSeconds(3600)
        );
        when(connectionRepository.findByWorkspaceIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(existingConnection));
        whenEncryptsTokens();

        // when
        service.connect(
                authorization(),
                token()
        );

        // then
        verify(connectionRepository).save(existingConnection);
        assertThat(existingConnection).extracting(
                NotionConnection::getAccessTokenCiphertext,
                NotionConnection::getRefreshTokenCiphertext,
                NotionConnection::getNotionWorkspaceId,
                NotionConnection::getNotionWorkspaceName,
                NotionConnection::getNotionWorkspaceIcon,
                NotionConnection::getBotId,
                NotionConnection::getOwnerType,
                NotionConnection::getOwnerUserId,
                NotionConnection::getDuplicatedTemplateId,
                NotionConnection::getRequestId,
                NotionConnection::getAuthorizingMemberId,
                NotionConnection::getUpdatedAt
        )
                .containsExactly(
                        ACCESS_TOKEN_CIPHERTEXT,
                        REFRESH_TOKEN_CIPHERTEXT,
                        "notion-workspace-id",
                        "Knot Notion",
                        "https://static.notion.test/icon.png",
                        "bot-id",
                        "user",
                        "notion-owner-user-id",
                        "template-id",
                        "request-id",
                        MEMBER_ID,
                        CURRENT_TIME
                );
    }

    @DisplayName("더 최신 OAuth 인증 흐름이 있으면 connection 저장을 거부한다")
    @Test
    void connect_failure_newerAuthorizationExists() {
        // given
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                CURRENT_TIME
                        )
                )
        );
        when(
                authorizationRepository.existsNewerAuthorization(
                        WORKSPACE_ID,
                        AUTHORIZATION_ID
                )
        ).thenReturn(true);

        // when
        ThrowingCallable action = () -> service.connect(
                authorization(),
                token()
        );

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.STALE_NOTION_OAUTH_AUTHORIZATION);
        verifyNoInteractions(
                workspaceMemberRepository,
                secretProtector,
                connectionRepository
        );
    }

    @DisplayName("인증을 시작한 멤버가 더 이상 OWNER가 아니면 connection 저장을 거부한다")
    @Test
    void connect_failure_authorizingMemberNoLongerOwner() {
        // given
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                CURRENT_TIME
                        )
                )
        );
        when(
                authorizationRepository.existsNewerAuthorization(
                        WORKSPACE_ID,
                        AUTHORIZATION_ID
                )
        ).thenReturn(false);
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(false);

        // when
        ThrowingCallable action = () -> service.connect(
                authorization(),
                token()
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        verifyNoInteractions(secretProtector);
        verify(
                connectionRepository,
                never()
        ).save(any());
    }

    private void allowCurrentOwnerAuthorization() {
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                CURRENT_TIME
                        )
                )
        );
        when(
                authorizationRepository.existsNewerAuthorization(
                        WORKSPACE_ID,
                        AUTHORIZATION_ID
                )
        ).thenReturn(false);
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(true);
    }

    private void whenEncryptsTokens() {
        when(
                secretProtector.encrypt(
                        WORKSPACE_ID,
                        NotionOAuthCredentialKind.ACCESS_TOKEN,
                        ACCESS_TOKEN
                )
        ).thenReturn(ACCESS_TOKEN_CIPHERTEXT);
        when(
                secretProtector.encrypt(
                        WORKSPACE_ID,
                        NotionOAuthCredentialKind.REFRESH_TOKEN,
                        REFRESH_TOKEN
                )
        ).thenReturn(REFRESH_TOKEN_CIPHERTEXT);
    }

    private NotionOAuthAuthorizationContext authorization() {
        return new NotionOAuthAuthorizationContext(
                AUTHORIZATION_ID,
                WORKSPACE_ID,
                MEMBER_ID,
                CALLBACK_URI
        );
    }

    private NotionOAuthToken token() {
        return new NotionOAuthToken(
                ACCESS_TOKEN,
                REFRESH_TOKEN,
                "notion-workspace-id",
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                "user",
                "notion-owner-user-id",
                "template-id",
                "request-id"
        );
    }
}

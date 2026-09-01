package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.ContentSourceAuthorizationContext;
import com.knot.backend.workspace.application.dto.result.AuthorizedContentSource;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationOwnerType;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationRepository;
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

class ContentSourceConnectionServiceTest {
    private static final Long AUTHORIZATION_ID = 7L;
    private static final Long WORKSPACE_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final URI CALLBACK_URI = URI.create("https://api.knot.test/api/v1/notion/oauth/callback");
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00.123456789Z");
    private static final Instant CURRENT_TIME = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final String ACCESS_CREDENTIAL = "access-token";
    private static final String REFRESH_CREDENTIAL = "refresh-token";
    private static final String ACCESS_CREDENTIAL_CIPHERTEXT = "access-envelope";
    private static final String REFRESH_CREDENTIAL_CIPHERTEXT = "refresh-envelope";
    private static final ContentSourceProvider PROVIDER = ContentSourceProvider.NOTION;

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final ContentSourceAuthorizationRepository authorizationRepository = mock(
            ContentSourceAuthorizationRepository.class
    );
    private final ContentSourceConnectionRepository connectionRepository = mock(
            ContentSourceConnectionRepository.class
    );
    private final ContentSourceSecretProtector secretProtector = mock(ContentSourceSecretProtector.class);
    private final ContentSourceConnectionService service = new ContentSourceConnectionService(
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
        when(
                connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                        WORKSPACE_ID,
                        PROVIDER
                )
        ).thenReturn(Optional.empty());
        whenEncryptsTokens();

        // when
        service.connect(
                authorization(),
                token()
        );

        // then
        ArgumentCaptor<ContentSourceConnection> connectionCaptor = ArgumentCaptor
                .forClass(ContentSourceConnection.class);
        verify(connectionRepository).save(connectionCaptor.capture());
        assertThat(connectionCaptor.getValue()).extracting(
                ContentSourceConnection::getWorkspaceId,
                ContentSourceConnection::getProvider,
                ContentSourceConnection::getAccessCredentialCiphertext,
                ContentSourceConnection::getRefreshCredentialCiphertext,
                ContentSourceConnection::getExternalSourceId,
                ContentSourceConnection::getExternalSourceName,
                ContentSourceConnection::getExternalSourceIcon,
                ContentSourceConnection::getProviderConnectionId,
                ContentSourceConnection::getAuthorizationOwnerType,
                ContentSourceConnection::getAuthorizationOwnerId,
                ContentSourceConnection::getExternalTemplateId,
                ContentSourceConnection::getProviderRequestId,
                ContentSourceConnection::getAuthorizingMemberId,
                ContentSourceConnection::getCreatedAt,
                ContentSourceConnection::getUpdatedAt
        )
                .containsExactly(
                        WORKSPACE_ID,
                        PROVIDER,
                        ACCESS_CREDENTIAL_CIPHERTEXT,
                        REFRESH_CREDENTIAL_CIPHERTEXT,
                        "notion-workspace-id",
                        "Knot Notion",
                        "https://static.notion.test/icon.png",
                        "bot-id",
                        ContentSourceAuthorizationOwnerType.USER,
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
        when(
                connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                        WORKSPACE_ID,
                        PROVIDER
                )
        ).thenReturn(Optional.empty());
        when(
                secretProtector.encrypt(
                        WORKSPACE_ID,
                        PROVIDER,
                        ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                        ACCESS_CREDENTIAL
                )
        ).thenReturn(ACCESS_CREDENTIAL_CIPHERTEXT);
        AuthorizedContentSource token = new AuthorizedContentSource(
                PROVIDER,
                ACCESS_CREDENTIAL,
                null,
                "notion-workspace-id",
                "Knot Notion",
                null,
                "bot-id",
                ContentSourceAuthorizationOwnerType.USER,
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
        ArgumentCaptor<ContentSourceConnection> connectionCaptor = ArgumentCaptor
                .forClass(ContentSourceConnection.class);
        verify(connectionRepository).save(connectionCaptor.capture());
        assertThat(
                connectionCaptor.getValue()
                        .getRefreshCredentialCiphertext()
        ).isNull();
        verify(
                secretProtector,
                never()
        ).encrypt(
                WORKSPACE_ID,
                PROVIDER,
                ContentSourceCredentialKind.REFRESH_CREDENTIAL,
                null
        );
    }

    @DisplayName("이미 연결된 워크스페이스는 새 Notion connection 값으로 교체한다")
    @Test
    void connect_success_replacesExistingConnection() {
        // given
        allowCurrentOwnerAuthorization();
        ContentSourceConnection existingConnection = ContentSourceConnection.create(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                "old-access-envelope",
                "old-refresh-envelope",
                "old-notion-workspace-id",
                "Old Notion",
                null,
                "old-bot-id",
                ContentSourceAuthorizationOwnerType.USER,
                "old-notion-owner-user-id",
                null,
                null,
                3L,
                CURRENT_TIME.minusSeconds(3600)
        );
        when(
                connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                        WORKSPACE_ID,
                        PROVIDER
                )
        ).thenReturn(Optional.of(existingConnection));
        whenEncryptsTokens();

        // when
        service.connect(
                authorization(),
                token()
        );

        // then
        verify(connectionRepository).save(existingConnection);
        assertThat(existingConnection).extracting(
                ContentSourceConnection::getAccessCredentialCiphertext,
                ContentSourceConnection::getRefreshCredentialCiphertext,
                ContentSourceConnection::getExternalSourceId,
                ContentSourceConnection::getExternalSourceName,
                ContentSourceConnection::getExternalSourceIcon,
                ContentSourceConnection::getProviderConnectionId,
                ContentSourceConnection::getAuthorizationOwnerType,
                ContentSourceConnection::getAuthorizationOwnerId,
                ContentSourceConnection::getExternalTemplateId,
                ContentSourceConnection::getProviderRequestId,
                ContentSourceConnection::getAuthorizingMemberId,
                ContentSourceConnection::getUpdatedAt
        )
                .containsExactly(
                        ACCESS_CREDENTIAL_CIPHERTEXT,
                        REFRESH_CREDENTIAL_CIPHERTEXT,
                        "notion-workspace-id",
                        "Knot Notion",
                        "https://static.notion.test/icon.png",
                        "bot-id",
                        ContentSourceAuthorizationOwnerType.USER,
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
                        PROVIDER,
                        AUTHORIZATION_ID
                )
        ).thenReturn(true);

        // when
        ThrowingCallable action = () -> service.connect(
                authorization(),
                token()
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ContentSourceException.class)
                .extracting(exception -> ((ContentSourceException) exception).getErrorCode())
                .isEqualTo(ContentSourceErrorCode.STALE_CONTENT_SOURCE_AUTHORIZATION);
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
                        PROVIDER,
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
                        PROVIDER,
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
                        PROVIDER,
                        ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                        ACCESS_CREDENTIAL
                )
        ).thenReturn(ACCESS_CREDENTIAL_CIPHERTEXT);
        when(
                secretProtector.encrypt(
                        WORKSPACE_ID,
                        PROVIDER,
                        ContentSourceCredentialKind.REFRESH_CREDENTIAL,
                        REFRESH_CREDENTIAL
                )
        ).thenReturn(REFRESH_CREDENTIAL_CIPHERTEXT);
    }

    private ContentSourceAuthorizationContext authorization() {
        return new ContentSourceAuthorizationContext(
                AUTHORIZATION_ID,
                WORKSPACE_ID,
                PROVIDER,
                MEMBER_ID,
                CALLBACK_URI
        );
    }

    private AuthorizedContentSource token() {
        return new AuthorizedContentSource(
                PROVIDER,
                ACCESS_CREDENTIAL,
                REFRESH_CREDENTIAL,
                "notion-workspace-id",
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                ContentSourceAuthorizationOwnerType.USER,
                "notion-owner-user-id",
                "template-id",
                "request-id"
        );
    }
}

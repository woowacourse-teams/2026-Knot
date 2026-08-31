package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.NotionOAuthAuthorizationContext;
import com.knot.backend.workspace.application.dto.result.NotionOAuthAuthorizationResult;
import com.knot.backend.workspace.domain.NotionErrorCode;
import com.knot.backend.workspace.domain.NotionException;
import com.knot.backend.workspace.domain.NotionOAuthAuthorization;
import com.knot.backend.workspace.domain.NotionOAuthAuthorizationRepository;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotionOAuthAuthorizationServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00.123456789Z");
    private static final Instant CURRENT_TIME = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final URI CALLBACK_URI = URI.create("https://api.knot.test/api/v1/notion/oauth/callback");
    private static final URI AUTHORIZATION_URI = URI
            .create("https://api.notion.com/v1/oauth/authorize?state=raw-state");
    private static final String RAW_STATE = "raw-state";
    private static final String STATE_HASH = "state-hash";

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final NotionOAuthAuthorizationRepository authorizationRepository = mock(
            NotionOAuthAuthorizationRepository.class
    );
    private final NotionOAuthStateGenerator stateGenerator = mock(NotionOAuthStateGenerator.class);
    private final NotionOAuthSecretProtector secretProtector = mock(NotionOAuthSecretProtector.class);
    private final NotionOAuthClient oAuthClient = mock(NotionOAuthClient.class);
    private final NotionOAuthSettings settings = mock(NotionOAuthSettings.class);
    private final NotionOAuthAuthorizationService service = new NotionOAuthAuthorizationService(
            workspaceRepository,
            workspaceMemberRepository,
            authorizationRepository,
            stateGenerator,
            secretProtector,
            oAuthClient,
            settings,
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            )
    );

    @DisplayName("OWNER가 OAuth 시작을 요청하면 state를 저장하고 authorization URL을 반환한다")
    @Test
    void start_success_ownerCreatesAuthorization() {
        // given
        allowOwner();
        when(authorizationRepository.findPendingByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(stateGenerator.generate()).thenReturn(RAW_STATE);
        when(secretProtector.hashState(RAW_STATE)).thenReturn(STATE_HASH);
        when(settings.callbackUri()).thenReturn(CALLBACK_URI);
        when(settings.stateTtl()).thenReturn(STATE_TTL);
        when(
                oAuthClient.createAuthorizationUri(
                        RAW_STATE,
                        CALLBACK_URI
                )
        ).thenReturn(AUTHORIZATION_URI);

        // when
        NotionOAuthAuthorizationResult result = service.start(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        ArgumentCaptor<NotionOAuthAuthorization> authorizationCaptor = ArgumentCaptor
                .forClass(NotionOAuthAuthorization.class);
        verify(authorizationRepository).save(authorizationCaptor.capture());
        assertThat(result.authorizationUri()).isEqualTo(AUTHORIZATION_URI);
        assertThat(authorizationCaptor.getValue()).extracting(
                NotionOAuthAuthorization::getWorkspaceId,
                NotionOAuthAuthorization::getAuthorizingMemberId,
                NotionOAuthAuthorization::getStateHash,
                NotionOAuthAuthorization::getCallbackUri,
                NotionOAuthAuthorization::getCreatedAt,
                NotionOAuthAuthorization::getExpiresAt
        )
                .containsExactly(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        STATE_HASH,
                        CALLBACK_URI,
                        CURRENT_TIME,
                        CURRENT_TIME.plus(STATE_TTL)
                );
    }

    @DisplayName("새 OAuth 시작은 기존 pending state를 무효화한다")
    @Test
    void start_success_invalidatesPreviousPendingAuthorization() {
        // given
        allowOwner();
        NotionOAuthAuthorization previousAuthorization = createAuthorization(
                "previous-state-hash",
                CURRENT_TIME.minusSeconds(60),
                CURRENT_TIME.plusSeconds(60)
        );
        when(authorizationRepository.findPendingByWorkspaceId(WORKSPACE_ID))
                .thenReturn(Optional.of(previousAuthorization));
        when(stateGenerator.generate()).thenReturn(RAW_STATE);
        when(secretProtector.hashState(RAW_STATE)).thenReturn(STATE_HASH);
        when(settings.callbackUri()).thenReturn(CALLBACK_URI);
        when(settings.stateTtl()).thenReturn(STATE_TTL);
        when(
                oAuthClient.createAuthorizationUri(
                        RAW_STATE,
                        CALLBACK_URI
                )
        ).thenReturn(AUTHORIZATION_URI);

        // when
        service.start(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(previousAuthorization.getInvalidatedAt()).isEqualTo(CURRENT_TIME);
        verify(authorizationRepository).save(previousAuthorization);
    }

    @DisplayName("OWNER가 아니면 OAuth 시작을 거부한다")
    @Test
    void start_failure_nonOwner() {
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
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(false);

        // when
        ThrowingCallable action = () -> service.start(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        verify(
                authorizationRepository,
                never()
        ).save(any());
        verifyNoInteractions(
                stateGenerator,
                oAuthClient
        );
    }

    @DisplayName("유효한 OAuth state를 소비하면 callback 처리 컨텍스트를 반환한다")
    @Test
    void consume_success_returnsAuthorizationContext() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization(
                STATE_HASH,
                CURRENT_TIME.minusSeconds(60),
                CURRENT_TIME.plusSeconds(60)
        );
        when(secretProtector.hashState(RAW_STATE)).thenReturn(STATE_HASH);
        when(authorizationRepository.findByStateHashForUpdate(STATE_HASH)).thenReturn(Optional.of(authorization));
        when(authorizationRepository.save(authorization)).thenReturn(authorization);

        // when
        NotionOAuthAuthorizationContext context = service.consume(RAW_STATE);

        // then
        assertThat(authorization.getConsumedAt()).isEqualTo(CURRENT_TIME);
        assertThat(context.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(context.authorizingMemberId()).isEqualTo(MEMBER_ID);
        assertThat(context.callbackUri()).isEqualTo(CALLBACK_URI);
    }

    @DisplayName("만료된 OAuth state 소비를 거부한다")
    @Test
    void consume_failure_expiredState() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization(
                STATE_HASH,
                CURRENT_TIME.minusSeconds(120),
                CURRENT_TIME
        );
        when(secretProtector.hashState(RAW_STATE)).thenReturn(STATE_HASH);
        when(authorizationRepository.findByStateHashForUpdate(STATE_HASH)).thenReturn(Optional.of(authorization));

        // when
        ThrowingCallable action = () -> service.consume(RAW_STATE);

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.EXPIRED_NOTION_OAUTH_STATE);
        verify(
                authorizationRepository,
                never()
        ).save(any());
    }

    @DisplayName("이미 소비된 OAuth state 재사용을 거부한다")
    @Test
    void consume_failure_reusedState() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization(
                STATE_HASH,
                CURRENT_TIME.minusSeconds(60),
                CURRENT_TIME.plusSeconds(60)
        );
        authorization.consume(CURRENT_TIME.minusSeconds(1));
        when(secretProtector.hashState(RAW_STATE)).thenReturn(STATE_HASH);
        when(authorizationRepository.findByStateHashForUpdate(STATE_HASH)).thenReturn(Optional.of(authorization));

        // when
        ThrowingCallable action = () -> service.consume(RAW_STATE);

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
        verify(
                authorizationRepository,
                never()
        ).save(any());
    }

    @DisplayName("저장된 OAuth state가 없으면 소비를 거부한다")
    @Test
    void consume_failure_unknownState() {
        // given
        when(secretProtector.hashState(RAW_STATE)).thenReturn(STATE_HASH);
        when(authorizationRepository.findByStateHashForUpdate(STATE_HASH)).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.consume(RAW_STATE);

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
        verify(
                authorizationRepository,
                never()
        ).save(any());
    }

    private void allowOwner() {
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                CURRENT_TIME
                        )
                )
        );
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(true);
    }

    private NotionOAuthAuthorization createAuthorization(
            String stateHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        return NotionOAuthAuthorization.create(
                WORKSPACE_ID,
                MEMBER_ID,
                stateHash,
                CALLBACK_URI,
                createdAt,
                expiresAt
        );
    }
}

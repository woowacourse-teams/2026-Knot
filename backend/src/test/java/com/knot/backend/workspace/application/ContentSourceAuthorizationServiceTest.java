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
import com.knot.backend.workspace.application.dto.result.ContentSourceAuthorizationResult;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceAuthorization;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
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

class ContentSourceAuthorizationServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00.123456789Z");
    private static final Instant CURRENT_TIME = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final ContentSourceProvider PROVIDER = ContentSourceProvider.NOTION;
    private static final URI CALLBACK_URI = URI.create("https://api.knot.test/api/v1/notion/oauth/callback");
    private static final URI AUTHORIZATION_URI = URI
            .create("https://api.notion.com/v1/oauth/authorize?state=raw-state");
    private static final String RAW_STATE = "raw-state";
    private static final String STATE_HASH = "state-hash";

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final ContentSourceAuthorizationRepository authorizationRepository = mock(
            ContentSourceAuthorizationRepository.class
    );
    private final ContentSourceStateGenerator stateGenerator = mock(ContentSourceStateGenerator.class);
    private final ContentSourceSecretProtector secretProtector = mock(ContentSourceSecretProtector.class);
    private final ContentSourceAuthorizationClient oAuthClient = mock(ContentSourceAuthorizationClient.class);
    private final ContentSourceAuthorizationSettings settings = mock(ContentSourceAuthorizationSettings.class);
    private final ContentSourceAuthorizationService service = new ContentSourceAuthorizationService(
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
        when(oAuthClient.provider()).thenReturn(PROVIDER);
        when(
                authorizationRepository.findPendingByWorkspaceIdAndProvider(
                        WORKSPACE_ID,
                        PROVIDER
                )
        ).thenReturn(Optional.empty());
        when(stateGenerator.generate()).thenReturn(RAW_STATE);
        when(
                secretProtector.hashState(
                        PROVIDER,
                        RAW_STATE
                )
        ).thenReturn(STATE_HASH);
        when(settings.callbackUri()).thenReturn(CALLBACK_URI);
        when(settings.stateTtl()).thenReturn(STATE_TTL);
        when(
                oAuthClient.createAuthorizationUri(
                        PROVIDER,
                        RAW_STATE,
                        CALLBACK_URI
                )
        ).thenReturn(AUTHORIZATION_URI);

        // when
        ContentSourceAuthorizationResult result = service.start(
                WORKSPACE_ID,
                MEMBER_ID,
                PROVIDER
        );

        // then
        ArgumentCaptor<ContentSourceAuthorization> authorizationCaptor = ArgumentCaptor
                .forClass(ContentSourceAuthorization.class);
        verify(authorizationRepository).save(authorizationCaptor.capture());
        assertThat(result.authorizationUri()).isEqualTo(AUTHORIZATION_URI);
        assertThat(authorizationCaptor.getValue()).extracting(
                ContentSourceAuthorization::getWorkspaceId,
                ContentSourceAuthorization::getProvider,
                ContentSourceAuthorization::getAuthorizingMemberId,
                ContentSourceAuthorization::getStateHash,
                ContentSourceAuthorization::getCallbackUri,
                ContentSourceAuthorization::getCreatedAt,
                ContentSourceAuthorization::getExpiresAt
        )
                .containsExactly(
                        WORKSPACE_ID,
                        PROVIDER,
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
        when(oAuthClient.provider()).thenReturn(PROVIDER);
        ContentSourceAuthorization previousAuthorization = createAuthorization(
                "previous-state-hash",
                CURRENT_TIME.minusSeconds(60),
                CURRENT_TIME.plusSeconds(60)
        );
        when(
                authorizationRepository.findPendingByWorkspaceIdAndProvider(
                        WORKSPACE_ID,
                        PROVIDER
                )
        ).thenReturn(Optional.of(previousAuthorization));
        when(stateGenerator.generate()).thenReturn(RAW_STATE);
        when(
                secretProtector.hashState(
                        PROVIDER,
                        RAW_STATE
                )
        ).thenReturn(STATE_HASH);
        when(settings.callbackUri()).thenReturn(CALLBACK_URI);
        when(settings.stateTtl()).thenReturn(STATE_TTL);
        when(
                oAuthClient.createAuthorizationUri(
                        PROVIDER,
                        RAW_STATE,
                        CALLBACK_URI
                )
        ).thenReturn(AUTHORIZATION_URI);

        // when
        service.start(
                WORKSPACE_ID,
                MEMBER_ID,
                PROVIDER
        );

        // then
        assertThat(previousAuthorization.getInvalidatedAt()).isEqualTo(CURRENT_TIME);
        verify(authorizationRepository).save(previousAuthorization);
    }

    @DisplayName("OWNER가 아니면 OAuth 시작을 거부한다")
    @Test
    void start_failure_nonOwner() {
        // given
        when(oAuthClient.provider()).thenReturn(PROVIDER);
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
                MEMBER_ID,
                PROVIDER
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        verify(
                authorizationRepository,
                never()
        ).save(any());
        verifyNoInteractions(stateGenerator);
    }

    @DisplayName("유효한 OAuth state를 소비하면 callback 처리 컨텍스트를 반환한다")
    @Test
    void consume_success_returnsAuthorizationContext() {
        // given
        ContentSourceAuthorization authorization = createAuthorization(
                STATE_HASH,
                CURRENT_TIME.minusSeconds(60),
                CURRENT_TIME.plusSeconds(60)
        );
        when(oAuthClient.provider()).thenReturn(PROVIDER);
        when(
                secretProtector.hashState(
                        PROVIDER,
                        RAW_STATE
                )
        ).thenReturn(STATE_HASH);
        when(
                authorizationRepository.findByProviderAndStateHashForUpdate(
                        PROVIDER,
                        STATE_HASH
                )
        ).thenReturn(Optional.of(authorization));
        when(authorizationRepository.save(authorization)).thenReturn(authorization);

        // when
        ContentSourceAuthorizationContext context = service.consume(
                PROVIDER,
                RAW_STATE
        );

        // then
        assertThat(authorization.getConsumedAt()).isEqualTo(CURRENT_TIME);
        assertThat(context.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(context.provider()).isEqualTo(PROVIDER);
        assertThat(context.authorizingMemberId()).isEqualTo(MEMBER_ID);
        assertThat(context.callbackUri()).isEqualTo(CALLBACK_URI);
    }

    @DisplayName("만료된 OAuth state 소비를 거부한다")
    @Test
    void consume_failure_expiredState() {
        // given
        ContentSourceAuthorization authorization = createAuthorization(
                STATE_HASH,
                CURRENT_TIME.minusSeconds(120),
                CURRENT_TIME
        );
        when(oAuthClient.provider()).thenReturn(PROVIDER);
        when(
                secretProtector.hashState(
                        PROVIDER,
                        RAW_STATE
                )
        ).thenReturn(STATE_HASH);
        when(
                authorizationRepository.findByProviderAndStateHashForUpdate(
                        PROVIDER,
                        STATE_HASH
                )
        ).thenReturn(Optional.of(authorization));

        // when
        ThrowingCallable action = () -> service.consume(
                PROVIDER,
                RAW_STATE
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ContentSourceException.class)
                .extracting(exception -> ((ContentSourceException) exception).getErrorCode())
                .isEqualTo(ContentSourceErrorCode.EXPIRED_CONTENT_SOURCE_AUTHORIZATION);
        verify(
                authorizationRepository,
                never()
        ).save(any());
    }

    @DisplayName("이미 소비된 OAuth state 재사용을 거부한다")
    @Test
    void consume_failure_reusedState() {
        // given
        ContentSourceAuthorization authorization = createAuthorization(
                STATE_HASH,
                CURRENT_TIME.minusSeconds(60),
                CURRENT_TIME.plusSeconds(60)
        );
        authorization.consume(CURRENT_TIME.minusSeconds(1));
        when(oAuthClient.provider()).thenReturn(PROVIDER);
        when(
                secretProtector.hashState(
                        PROVIDER,
                        RAW_STATE
                )
        ).thenReturn(STATE_HASH);
        when(
                authorizationRepository.findByProviderAndStateHashForUpdate(
                        PROVIDER,
                        STATE_HASH
                )
        ).thenReturn(Optional.of(authorization));

        // when
        ThrowingCallable action = () -> service.consume(
                PROVIDER,
                RAW_STATE
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ContentSourceException.class)
                .extracting(exception -> ((ContentSourceException) exception).getErrorCode())
                .isEqualTo(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        verify(
                authorizationRepository,
                never()
        ).save(any());
    }

    @DisplayName("저장된 OAuth state가 없으면 소비를 거부한다")
    @Test
    void consume_failure_unknownState() {
        // given
        when(oAuthClient.provider()).thenReturn(PROVIDER);
        when(
                secretProtector.hashState(
                        PROVIDER,
                        RAW_STATE
                )
        ).thenReturn(STATE_HASH);
        when(
                authorizationRepository.findByProviderAndStateHashForUpdate(
                        PROVIDER,
                        STATE_HASH
                )
        ).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.consume(
                PROVIDER,
                RAW_STATE
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ContentSourceException.class)
                .extracting(exception -> ((ContentSourceException) exception).getErrorCode())
                .isEqualTo(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
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

    private ContentSourceAuthorization createAuthorization(
            String stateHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        return ContentSourceAuthorization.create(
                WORKSPACE_ID,
                PROVIDER,
                MEMBER_ID,
                stateHash,
                CALLBACK_URI,
                createdAt,
                expiresAt
        );
    }
}

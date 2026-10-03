package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.application.dto.result.AuthRefreshResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthSession;
import com.knot.backend.auth.domain.AuthSessionRepository;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.RefreshToken;
import com.knot.backend.auth.domain.RefreshTokenHistory;
import com.knot.backend.auth.domain.RefreshTokenHistoryRepository;
import com.knot.backend.auth.domain.RefreshTokenProvider;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AuthSessionRefreshServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");
    private static final Instant SESSION_CREATED_AT = NOW.minus(Duration.ofDays(2));
    private static final String CURRENT_TOKEN_VALUE = "current-refresh-token";
    private static final String CURRENT_TOKEN_HASH = "a".repeat(64);
    private static final String NEXT_TOKEN_HASH = "b".repeat(64);

    private final AuthSessionRepository authSessionRepository = mock(AuthSessionRepository.class);
    private final RefreshTokenHistoryRepository historyRepository = mock(RefreshTokenHistoryRepository.class);
    private final RefreshTokenProvider refreshTokenProvider = mock(RefreshTokenProvider.class);
    private final AuthTokenProvider authTokenProvider = mock(AuthTokenProvider.class);
    private final MemberService memberService = mock(MemberService.class);
    private final Clock clock = Clock.fixed(
            NOW,
            ZoneOffset.UTC
    );
    private final AuthSessionRefreshService service = new AuthSessionRefreshService(
            authSessionRepository,
            historyRepository,
            refreshTokenProvider,
            authTokenProvider,
            memberService,
            clock
    );

    @Test
    @DisplayName("활성 refresh 토큰을 회전하고 access token을 다시 발급한다")
    void refresh_success_rotatesTokenAndIssuesAccessToken() {
        // given
        AuthSession session = activeSession();
        RefreshToken currentToken = RefreshToken.of(
                CURRENT_TOKEN_VALUE,
                CURRENT_TOKEN_HASH
        );
        RefreshToken replacementToken = RefreshToken.of(
                "replacement-refresh-token",
                NEXT_TOKEN_HASH
        );
        Member member = activeMember(session.getMemberId());
        when(refreshTokenProvider.identify(CURRENT_TOKEN_VALUE)).thenReturn(currentToken);
        when(historyRepository.findByRefreshTokenHash(CURRENT_TOKEN_HASH)).thenReturn(Optional.empty());
        when(authSessionRepository.findByRefreshTokenHashForUpdate(CURRENT_TOKEN_HASH))
                .thenReturn(Optional.of(session));
        when(memberService.findById(session.getMemberId())).thenReturn(Optional.of(member));
        when(refreshTokenProvider.issue(NOW.plus(Duration.ofDays(7)))).thenReturn(replacementToken);
        when(authTokenProvider.issue(any(AuthenticatedMember.class))).thenReturn("new-access-token");

        // when
        Optional<AuthRefreshResult> result = service.refresh(CURRENT_TOKEN_VALUE);

        // then
        assertThat(result).contains(
                new AuthRefreshResult(
                        "new-access-token",
                        "replacement-refresh-token",
                        Duration.ofDays(7)
                )
        );
        assertThat(session.getRefreshTokenHash()).isEqualTo(NEXT_TOKEN_HASH);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        ArgumentCaptor<RefreshTokenHistory> historyCaptor = ArgumentCaptor.forClass(RefreshTokenHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(
                historyCaptor.getValue()
                        .getAuthSessionId()
        ).isEqualTo(session.getId());
        assertThat(
                historyCaptor.getValue()
                        .getRefreshTokenHash()
        ).isEqualTo(CURRENT_TOKEN_HASH);
        assertThat(
                historyCaptor.getValue()
                        .getConsumedAt()
        ).isEqualTo(NOW);
        verify(authSessionRepository).save(session);
        verify(refreshTokenProvider).validate(currentToken);
    }

    @Test
    @DisplayName("이미 소비한 refresh 토큰을 다시 받으면 해당 세션을 폐기하고 결과를 반환하지 않는다")
    void refresh_success_replayRevokesSessionFamily() {
        // given
        AuthSession session = activeSession();
        RefreshTokenHistory history = RefreshTokenHistory.create(
                session.getId(),
                CURRENT_TOKEN_HASH,
                NOW.minusSeconds(1)
        );
        when(refreshTokenProvider.identify(CURRENT_TOKEN_VALUE)).thenReturn(
                RefreshToken.of(
                        CURRENT_TOKEN_VALUE,
                        CURRENT_TOKEN_HASH
                )
        );
        when(historyRepository.findByRefreshTokenHash(CURRENT_TOKEN_HASH)).thenReturn(Optional.of(history));
        when(authSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        // when
        Optional<AuthRefreshResult> result = service.refresh(CURRENT_TOKEN_VALUE);

        // then
        assertThat(result).isEmpty();
        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        verify(authSessionRepository).save(session);
        verify(
                refreshTokenProvider,
                never()
        ).validate(any());
        verify(
                refreshTokenProvider,
                never()
        ).issue(any());
        verifyNoInteractions(
                memberService,
                authTokenProvider
        );
    }

    @Test
    @DisplayName("동시 회전으로 현재 세션 조회가 실패하면 소비 이력을 다시 확인해 세션을 폐기한다")
    void refresh_success_replayDetectedAfterConcurrentRotation() {
        // given
        AuthSession session = activeSession();
        RefreshToken token = RefreshToken.of(
                CURRENT_TOKEN_VALUE,
                CURRENT_TOKEN_HASH
        );
        RefreshTokenHistory history = RefreshTokenHistory.create(
                session.getId(),
                CURRENT_TOKEN_HASH,
                NOW
        );
        when(refreshTokenProvider.identify(CURRENT_TOKEN_VALUE)).thenReturn(token);
        when(historyRepository.findByRefreshTokenHash(CURRENT_TOKEN_HASH)).thenReturn(
                Optional.empty(),
                Optional.of(history)
        );
        when(authSessionRepository.findByRefreshTokenHashForUpdate(CURRENT_TOKEN_HASH)).thenReturn(Optional.empty());
        when(authSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

        // when
        Optional<AuthRefreshResult> result = service.refresh(CURRENT_TOKEN_VALUE);

        // then
        assertThat(result).isEmpty();
        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        verify(authSessionRepository).save(session);
        verify(refreshTokenProvider).validate(token);
    }

    @Test
    @DisplayName("만료되거나 위조된 refresh 토큰은 재발급하지 않는다")
    void refresh_failure_invalidToken() {
        // given
        RefreshToken token = RefreshToken.of(
                CURRENT_TOKEN_VALUE,
                CURRENT_TOKEN_HASH
        );
        when(refreshTokenProvider.identify(CURRENT_TOKEN_VALUE)).thenReturn(token);
        when(historyRepository.findByRefreshTokenHash(CURRENT_TOKEN_HASH)).thenReturn(Optional.empty());
        doThrow(new AuthException(AuthErrorCode.UNAUTHENTICATED)).when(refreshTokenProvider)
                .validate(token);

        // when
        Throwable thrown = catchThrowable(() -> service.refresh(CURRENT_TOKEN_VALUE));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.UNAUTHENTICATED)
        );
        verifyNoInteractions(
                authSessionRepository,
                memberService,
                authTokenProvider
        );
    }

    @Test
    @DisplayName("활성 세션에 없는 refresh 토큰은 재발급하지 않는다")
    void refresh_failure_unknownSession() {
        // given
        RefreshToken token = RefreshToken.of(
                CURRENT_TOKEN_VALUE,
                CURRENT_TOKEN_HASH
        );
        when(refreshTokenProvider.identify(CURRENT_TOKEN_VALUE)).thenReturn(token);
        when(historyRepository.findByRefreshTokenHash(CURRENT_TOKEN_HASH)).thenReturn(Optional.empty());
        when(authSessionRepository.findByRefreshTokenHashForUpdate(CURRENT_TOKEN_HASH)).thenReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.refresh(CURRENT_TOKEN_VALUE));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.UNAUTHENTICATED)
        );
        verifyNoInteractions(
                memberService,
                authTokenProvider
        );
    }

    @Test
    @DisplayName("탈퇴한 회원의 세션으로는 토큰을 재발급하지 않는다")
    void refresh_failure_withdrawnMember() {
        // given
        AuthSession session = activeSession();
        Member member = mock(Member.class);
        when(member.isDeleted()).thenReturn(true);
        when(refreshTokenProvider.identify(CURRENT_TOKEN_VALUE)).thenReturn(
                RefreshToken.of(
                        CURRENT_TOKEN_VALUE,
                        CURRENT_TOKEN_HASH
                )
        );
        when(historyRepository.findByRefreshTokenHash(CURRENT_TOKEN_HASH)).thenReturn(Optional.empty());
        when(authSessionRepository.findByRefreshTokenHashForUpdate(CURRENT_TOKEN_HASH))
                .thenReturn(Optional.of(session));
        when(memberService.findById(session.getMemberId())).thenReturn(Optional.of(member));

        // when
        Throwable thrown = catchThrowable(() -> service.refresh(CURRENT_TOKEN_VALUE));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.UNAUTHENTICATED)
        );
        assertThat(session.getRefreshTokenHash()).isEqualTo(CURRENT_TOKEN_HASH);
        verify(
                refreshTokenProvider,
                never()
        ).issue(any());
        verify(
                authSessionRepository,
                never()
        ).save(session);
    }

    private AuthSession activeSession() {
        AuthSession session = AuthSession.create(
                7L,
                CURRENT_TOKEN_HASH,
                SESSION_CREATED_AT
        );
        ReflectionTestUtils.setField(
                session,
                "id",
                11L
        );
        return session;
    }

    private Member activeMember(long memberId) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);
        when(member.getNickname()).thenReturn("octocat");
        when(member.getProfileImageUrl()).thenReturn("https://example.com/avatar");
        when(member.isDeleted()).thenReturn(false);
        return member;
    }
}

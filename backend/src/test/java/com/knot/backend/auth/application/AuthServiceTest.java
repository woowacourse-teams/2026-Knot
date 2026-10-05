package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.application.dto.result.AuthLoginResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.AuthSession;
import com.knot.backend.auth.domain.AuthSessionRepository;
import com.knot.backend.auth.domain.RefreshToken;
import com.knot.backend.auth.domain.RefreshTokenProvider;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import java.util.Optional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String REFRESH_TOKEN_HASH = "a".repeat(64);

    private final AuthSessionRepository sessionRepository = mock(AuthSessionRepository.class);
    private final RefreshTokenProvider refreshTokenProvider = mock(RefreshTokenProvider.class);
    private final Clock clock = Clock.fixed(
            NOW,
            ZoneOffset.UTC
    );

    @Test
    @DisplayName("등록된 OAuth identity가 있으면 일반 access token을 발급한다")
    void login_success_existingIdentity() {
        // given
        MemberService memberService = mock(MemberService.class);
        OAuthIdentityService oauthIdentityService = mock(OAuthIdentityService.class);
        MemberNicknameService memberNicknameService = mock(MemberNicknameService.class);
        AuthTokenProvider authTokenProvider = mock(AuthTokenProvider.class);
        AuthService service = new AuthService(
                memberService,
                oauthIdentityService,
                memberNicknameService,
                authTokenProvider,
                sessionRepository,
                refreshTokenProvider,
                clock
        );
        OAuthUser oauthUser = oauthUser();
        OAuthIdentity identity = OAuthIdentity.create(
                oauthUser,
                1L
        );
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("octocat");
        when(member.getProfileImageUrl()).thenReturn(null);
        when(
                oauthIdentityService.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB,
                        "42"
                )
        ).thenReturn(Optional.of(identity));
        when(memberService.findById(1L)).thenReturn(Optional.of(member));
        AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
                1L,
                "octocat",
                null
        );
        when(authTokenProvider.issue(authenticatedMember)).thenReturn("access-token");
        when(refreshTokenProvider.issue()).thenReturn(
                RefreshToken.of(
                        "refresh-token",
                        "a".repeat(64)
                )
        );

        // when
        AuthLoginResult result = service.login(oauthUser);

        // then
        assertThat(result.token()).isEqualTo("access-token");
        assertThat(result.requiresNickname()).isFalse();
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.refreshMaxAge()).isEqualTo(Duration.ofDays(7));
        verify(sessionRepository).save(any(AuthSession.class));
        verify(authTokenProvider).issue(authenticatedMember);
        verify(
                authTokenProvider,
                never()
        ).issueNickname(oauthUser);
    }

    @Test
    @DisplayName("등록된 OAuth identity가 없으면 닉네임 토큰을 발급한다")
    void login_success_missingIdentity() {
        // given
        MemberService memberService = mock(MemberService.class);
        OAuthIdentityService oauthIdentityService = mock(OAuthIdentityService.class);
        MemberNicknameService memberNicknameService = mock(MemberNicknameService.class);
        AuthTokenProvider authTokenProvider = mock(AuthTokenProvider.class);
        AuthService service = new AuthService(
                memberService,
                oauthIdentityService,
                memberNicknameService,
                authTokenProvider,
                sessionRepository,
                refreshTokenProvider,
                clock
        );
        OAuthUser oauthUser = oauthUser();
        when(
                oauthIdentityService.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB,
                        "42"
                )
        ).thenReturn(Optional.empty());
        when(authTokenProvider.issueNickname(oauthUser)).thenReturn("nickname-token");

        // when
        AuthLoginResult result = service.login(oauthUser);

        // then
        assertThat(result.token()).isEqualTo("nickname-token");
        assertThat(result.requiresNickname()).isTrue();
        assertThat(result.refreshToken()).isNull();
        verifyNoInteractions(
                sessionRepository,
                refreshTokenProvider
        );
        verify(
                memberService,
                never()
        ).findById(1L);
    }

    @Test
    @DisplayName("OAuth 사용자가 없으면 인증 사용자 오류를 발생시킨다")
    void login_failure_nullOAuthUser() {
        // given
        AuthService service = new AuthService(
                mock(MemberService.class),
                mock(OAuthIdentityService.class),
                mock(MemberNicknameService.class),
                mock(AuthTokenProvider.class),
                sessionRepository,
                refreshTokenProvider,
                clock
        );

        // when
        Throwable thrown = catchThrowable(() -> service.login(null));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_OAUTH_USER)
        );
    }

    @Test
    @DisplayName("OAuth identity에 연결된 member가 없으면 내부 인증 오류를 발생시킨다")
    void login_failure_memberNotFound() {
        // given
        MemberService memberService = mock(MemberService.class);
        OAuthIdentityService oauthIdentityService = mock(OAuthIdentityService.class);
        AuthService service = new AuthService(
                memberService,
                oauthIdentityService,
                mock(MemberNicknameService.class),
                mock(AuthTokenProvider.class),
                sessionRepository,
                refreshTokenProvider,
                clock
        );
        OAuthUser oauthUser = oauthUser();
        OAuthIdentity identity = OAuthIdentity.create(
                oauthUser,
                1L
        );
        when(
                oauthIdentityService.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB,
                        "42"
                )
        ).thenReturn(Optional.of(identity));
        when(memberService.findById(1L)).thenReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.login(oauthUser));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.MEMBER_NOT_FOUND_FOR_OAUTH_IDENTITY)
        );
    }

    @Test
    @DisplayName("닉네임 설정을 완료하면 access·refresh token과 인증 세션을 발급한다")
    void completeNicknameSetup_success() {
        // given
        AuthTokenProvider authTokenProvider = mock(AuthTokenProvider.class);
        MemberNicknameService memberNicknameService = mock(MemberNicknameService.class);
        AuthService service = new AuthService(
                mock(MemberService.class),
                mock(OAuthIdentityService.class),
                memberNicknameService,
                authTokenProvider,
                sessionRepository,
                refreshTokenProvider,
                clock
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                "https://example.com/avatar",
                NOW.minusSeconds(30)
        );
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("octocat");
        when(member.getProfileImageUrl()).thenReturn(null);
        when(authTokenProvider.authenticateNickname("nickname-token")).thenReturn(oauthUser);
        when(
                memberNicknameService.completeNicknameSetup(
                        oauthUser,
                        "octocat"
                )
        ).thenReturn(member);
        AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
                1L,
                "octocat",
                null
        );
        when(authTokenProvider.issue(authenticatedMember)).thenReturn("access-token");
        when(refreshTokenProvider.issue()).thenReturn(
                RefreshToken.of(
                        "refresh-token",
                        "a".repeat(64)
                )
        );

        // when
        AuthLoginResult result = service.completeNicknameSetup(
                new CompleteNicknameCommand(
                        "nickname-token",
                        "octocat"
                )
        );

        // then
        assertThat(result.token()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.refreshMaxAge()).isEqualTo(Duration.ofDays(7));
        verify(authTokenProvider).issue(authenticatedMember);
        verify(sessionRepository).save(
                argThat(
                        session -> session.getCreatedAt()
                                .equals(NOW)
                                && session.getAbsoluteExpiresAt()
                                        .equals(
                                                NOW.minusSeconds(30)
                                                        .plus(Duration.ofDays(30))
                                        )
                )
        );
    }

    @Test
    @DisplayName("온보딩 토큰 인증에 실패하면 회원과 인증 세션 생성을 요청하지 않는다")
    void completeNicknameSetup_failure_invalidNicknameToken() {
        // given
        AuthTokenProvider tokens = mock(AuthTokenProvider.class);
        MemberNicknameService nicknameService = mock(MemberNicknameService.class);
        AuthService service = new AuthService(
                mock(MemberService.class),
                mock(OAuthIdentityService.class),
                nicknameService,
                tokens,
                sessionRepository,
                refreshTokenProvider,
                clock
        );
        when(tokens.authenticateNickname("invalid-token")).thenThrow(new AuthException(AuthErrorCode.INVALID_JWT));

        // when & then
        assertThatThrownBy(
                () -> service.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                "invalid-token",
                                "octocat"
                        )
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
        verifyNoInteractions(
                nicknameService,
                refreshTokenProvider,
                sessionRepository
        );
    }

    @Test
    @DisplayName("최초 OAuth 로그인 시각이 없는 온보딩 정보는 가입 완료에 사용할 수 없다")
    void completeNicknameSetup_failure_missingOAuthLoginTime() {
        // given
        AuthTokenProvider tokens = mock(AuthTokenProvider.class);
        MemberNicknameService nicknameService = mock(MemberNicknameService.class);
        AuthService service = new AuthService(
                mock(MemberService.class),
                mock(OAuthIdentityService.class),
                nicknameService,
                tokens,
                sessionRepository,
                refreshTokenProvider,
                clock
        );
        when(tokens.authenticateNickname("nickname-token")).thenReturn(
                OAuthUser.of(
                        OAuthProvider.GITHUB,
                        "42",
                        null
                )
        );

        // when & then
        assertThatThrownBy(
                () -> service.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                "nickname-token",
                                "octocat"
                        )
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
        verifyNoInteractions(
                nicknameService,
                refreshTokenProvider,
                sessionRepository
        );
    }

    @Test
    @DisplayName("가입 완료 후 access token 발급에 실패하면 refresh token과 세션을 발급하지 않는다")
    void completeNicknameSetup_failure_accessTokenIssuance() {
        // given
        AuthTokenProvider tokens = mock(AuthTokenProvider.class);
        MemberNicknameService nicknameService = mock(MemberNicknameService.class);
        AuthService service = new AuthService(
                mock(MemberService.class),
                mock(OAuthIdentityService.class),
                nicknameService,
                tokens,
                sessionRepository,
                refreshTokenProvider,
                clock
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null,
                NOW
        );
        Member member = mock(Member.class);
        when(tokens.authenticateNickname("nickname-token")).thenReturn(oauthUser);
        when(
                nicknameService.completeNicknameSetup(
                        oauthUser,
                        "octocat"
                )
        ).thenReturn(member);
        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("octocat");
        when(tokens.issue(any())).thenThrow(new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR));

        // when & then
        assertThatThrownBy(
                () -> service.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                "nickname-token",
                                "octocat"
                        )
                )
        ).isInstanceOf(AuthException.class);
        verifyNoInteractions(
                refreshTokenProvider,
                sessionRepository
        );
    }

    @Test
    @DisplayName("가입 완료 후 refresh token 발급에 실패하면 인증 세션을 저장하지 않는다")
    void completeNicknameSetup_failure_refreshTokenIssuance() {
        // given
        AuthTokenProvider tokens = mock(AuthTokenProvider.class);
        MemberNicknameService nicknameService = mock(MemberNicknameService.class);
        AuthService service = new AuthService(
                mock(MemberService.class),
                mock(OAuthIdentityService.class),
                nicknameService,
                tokens,
                sessionRepository,
                refreshTokenProvider,
                clock
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null,
                NOW
        );
        Member member = mock(Member.class);
        when(tokens.authenticateNickname("nickname-token")).thenReturn(oauthUser);
        when(
                nicknameService.completeNicknameSetup(
                        oauthUser,
                        "octocat"
                )
        ).thenReturn(member);
        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("octocat");
        when(tokens.issue(any())).thenReturn("access-token");
        when(refreshTokenProvider.issue()).thenThrow(new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR));

        // when & then
        assertThatThrownBy(
                () -> service.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                "nickname-token",
                                "octocat"
                        )
                )
        ).isInstanceOf(AuthException.class);
        verifyNoInteractions(sessionRepository);
    }

    @Test
    @DisplayName("access 발급 실패 시 refresh와 세션을 만들지 않는다")
    void login_failure_accessIssuance() {
        // given
        AuthTokenProvider tokens = mock(AuthTokenProvider.class);
        AuthService service = registeredLoginService(tokens);
        when(tokens.issue(any())).thenThrow(new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR));

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser())).isInstanceOf(AuthException.class);
        verifyNoInteractions(
                refreshTokenProvider,
                sessionRepository
        );
    }

    @Test
    @DisplayName("refresh 발급 실패 시 세션을 저장하지 않는다")
    void login_failure_refreshIssuance() {
        // given
        AuthService service = registeredLoginService(mock(AuthTokenProvider.class));
        when(refreshTokenProvider.issue()).thenThrow(new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR));

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser())).isInstanceOf(AuthException.class);
        verifyNoInteractions(sessionRepository);
    }

    @Test
    @DisplayName("세션 저장 실패 시 성공 로그인 결과를 반환하지 않는다")
    void login_failure_sessionStorage() {
        // given
        AuthTokenProvider tokens = mock(AuthTokenProvider.class);
        AuthService service = registeredLoginService(tokens);
        when(tokens.issue(any())).thenReturn("access-token");
        when(refreshTokenProvider.issue()).thenReturn(
                RefreshToken.of(
                        "refresh-token",
                        "a".repeat(64)
                )
        );
        when(sessionRepository.save(any())).thenThrow(new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR));

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser())).isInstanceOf(AuthException.class);
    }

    private AuthService registeredLoginService(AuthTokenProvider tokens) {
        MemberService members = mock(MemberService.class);
        OAuthIdentityService identities = mock(OAuthIdentityService.class);
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("흑곰");
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(
                identities.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB,
                        "42"
                )
        ).thenReturn(
                Optional.of(
                        OAuthIdentity.create(
                                oauthUser(),
                                1L
                        )
                )
        );
        return new AuthService(
                members,
                identities,
                mock(MemberNicknameService.class),
                tokens,
                sessionRepository,
                refreshTokenProvider,
                clock
        );
    }

    @Test
    @DisplayName("탈퇴 회원은 OAuth 인증에 성공해도 토큰과 세션을 받지 못한다")
    void login_failure_withdrawnMember() {
        // given
        MemberService members = mock(MemberService.class);
        OAuthIdentityService identities = mock(OAuthIdentityService.class);
        AuthTokenProvider tokens = mock(AuthTokenProvider.class);
        Member member = mock(Member.class);
        when(member.isDeleted()).thenReturn(true);
        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("흑곰");
        when(tokens.issue(any())).thenReturn("access-token");
        when(refreshTokenProvider.issue()).thenReturn(
                RefreshToken.of(
                        "refresh-token",
                        "a".repeat(64)
                )
        );
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(
                identities.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB,
                        "42"
                )
        ).thenReturn(
                Optional.of(
                        OAuthIdentity.create(
                                oauthUser(),
                                1L
                        )
                )
        );
        AuthService service = new AuthService(
                members,
                identities,
                mock(MemberNicknameService.class),
                tokens,
                sessionRepository,
                refreshTokenProvider,
                clock
        );

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser())).isInstanceOf(AuthException.class);
        verifyNoInteractions(
                tokens,
                refreshTokenProvider,
                sessionRepository
        );
    }

    @Test
    @DisplayName("유효한 refresh token으로 현재 로그인 세션을 폐기한다")
    void logout_success_activeSession() {
        // given
        AuthSession session = AuthSession.create(
                7L,
                REFRESH_TOKEN_HASH,
                NOW.minusSeconds(60)
        );
        when(refreshTokenProvider.hash(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(sessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(session));
        AuthService service = logoutService();

        // when
        service.logout(REFRESH_TOKEN);

        // then
        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("refresh token이 없으면 세션 저장소에 접근하지 않는다")
    void logout_success_missingRefreshToken() {
        // given
        AuthService service = logoutService();

        // when
        service.logout(null);
        service.logout(" ");

        // then
        verifyNoInteractions(
                refreshTokenProvider,
                sessionRepository
        );
    }

    @Test
    @DisplayName("저장된 세션이 없는 refresh token은 추가 작업 없이 로그아웃한다")
    void logout_success_unknownRefreshToken() {
        // given
        when(refreshTokenProvider.hash(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(sessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.empty());
        AuthService service = logoutService();

        // when
        service.logout(REFRESH_TOKEN);

        // then
        verify(
                sessionRepository,
                never()
        ).save(any());
    }

    @Test
    @DisplayName("만료된 세션은 폐기 시각을 바꾸거나 저장하지 않는다")
    void logout_success_expiredSession() {
        // given
        AuthSession session = AuthSession.create(
                7L,
                REFRESH_TOKEN_HASH,
                NOW.minus(Duration.ofDays(8))
        );
        when(refreshTokenProvider.hash(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(sessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(session));
        AuthService service = logoutService();

        // when
        service.logout(REFRESH_TOKEN);

        // then
        assertThat(session.getRevokedAt()).isNull();
        verify(
                sessionRepository,
                never()
        ).save(any());
    }

    @Test
    @DisplayName("세션 폐기 저장에 실패하면 로그아웃 실패를 호출자에게 전달한다")
    void logout_failure_sessionStorage() {
        // given
        AuthSession session = AuthSession.create(
                7L,
                REFRESH_TOKEN_HASH,
                NOW.minusSeconds(60)
        );
        when(refreshTokenProvider.hash(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(sessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenThrow(new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR));
        AuthService service = logoutService();

        // when & then
        assertThatThrownBy(() -> service.logout(REFRESH_TOKEN)).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR)
        );
    }

    private AuthService logoutService() {
        return new AuthService(
                mock(MemberService.class),
                mock(OAuthIdentityService.class),
                mock(MemberNicknameService.class),
                mock(AuthTokenProvider.class),
                sessionRepository,
                refreshTokenProvider,
                clock
        );
    }

    private OAuthUser oauthUser() {
        return OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                "https://example.com/avatar"
        );
    }
}

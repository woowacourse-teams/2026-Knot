package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private final AuthSessionRepository sessionRepository = mock(AuthSessionRepository.class);
    private final RefreshTokenProvider refreshTokenProvider = mock(RefreshTokenProvider.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-01T00:00:00Z"),
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
    @DisplayName("닉네임 토큰과 닉네임으로 member를 생성하고 access token을 발급한다")
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
        OAuthUser oauthUser = oauthUser();
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

        // when
        String result = service.completeNicknameSetup(
                new CompleteNicknameCommand(
                        "nickname-token",
                        "octocat"
                )
        );

        // then
        assertThat(result).isEqualTo("access-token");
        verify(authTokenProvider).issue(authenticatedMember);
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

    private OAuthUser oauthUser() {
        return OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                "https://example.com/avatar"
        );
    }
}

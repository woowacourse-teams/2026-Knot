package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.infrastructure.jwt.JwtProvider;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberErrorCode;
import com.knot.backend.member.domain.MemberException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    @Test
    @DisplayName("OAuth 사용자로 member를 동기화하고 JWT를 발급한다")
    void login_success() {
        // given
        MemberService memberService = mock(MemberService.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);
        AuthService service = new AuthService(
                memberService,
                jwtProvider
        );
        OAuthUser oauthUser = OAuthUser.of(
                42L,
                "octocat",
                null
        );
        Member member = Member.create(oauthUser);
        when(memberService.login(oauthUser)).thenReturn(member);
        when(jwtProvider.issue(member)).thenReturn("jwt-token");

        // when
        String result = service.login(oauthUser);

        // then
        assertThat(result).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("OAuth 사용자가 없으면 인증 사용자 오류를 발생시킨다")
    void login_failure_nullOAuthUser() {
        // given
        AuthService service = new AuthService(
                mock(MemberService.class),
                mock(JwtProvider.class)
        );

        // when & then
        assertThatThrownBy(() -> service.login(null)).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_OAUTH_USER)
        );
    }

    @Test
    @DisplayName("member 로그인 실패를 인증 오류로 변환한다")
    void login_failure_memberError() {
        // given
        MemberService memberService = mock(MemberService.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);
        AuthService service = new AuthService(
                memberService,
                jwtProvider
        );
        OAuthUser oauthUser = OAuthUser.of(
                42L,
                "octocat",
                null
        );
        when(memberService.login(oauthUser))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_LOGIN_FAILED));

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser)).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED)
        );
    }

    @Test
    @DisplayName("인증 처리 중 예기치 못한 오류는 내부 인증 오류로 분류한다")
    void login_failure_unexpectedError() {
        // given
        MemberService memberService = mock(MemberService.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);
        AuthService service = new AuthService(
                memberService,
                jwtProvider
        );
        OAuthUser oauthUser = OAuthUser.of(
                42L,
                "octocat",
                null
        );
        when(memberService.login(oauthUser)).thenThrow(new IllegalStateException());

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser)).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(
                        exception.getErrorCode()
                                .getCode()
                ).isEqualTo("AUTHENTICATION_INTERNAL_ERROR")
        );
    }
}

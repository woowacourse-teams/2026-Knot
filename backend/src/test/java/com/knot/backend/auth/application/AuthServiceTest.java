package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    @Test
    @DisplayName("등록된 OAuth identity가 있으면 일반 access token을 발급한다")
    void login_existingIdentity_success() {
        // given
        MemberService memberService = mock(MemberService.class);
        OAuthIdentityService oauthIdentityService = mock(OAuthIdentityService.class);
        MemberNicknameService memberNicknameService = mock(MemberNicknameService.class);
        AuthTokenProvider authTokenProvider = mock(AuthTokenProvider.class);
        AuthService service = new AuthService(
                memberService,
                oauthIdentityService,
                memberNicknameService,
                authTokenProvider
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

        // when
        AuthLoginResult result = service.login(oauthUser);

        // then
        assertThat(result.getToken()).isEqualTo("access-token");
        assertThat(result.requiresNickname()).isFalse();
        verify(authTokenProvider).issue(authenticatedMember);
        verify(
                authTokenProvider,
                never()
        ).issueNickname(oauthUser);
    }

    @Test
    @DisplayName("등록된 OAuth identity가 없으면 닉네임 토큰을 발급한다")
    void login_missingIdentity_success() {
        // given
        MemberService memberService = mock(MemberService.class);
        OAuthIdentityService oauthIdentityService = mock(OAuthIdentityService.class);
        MemberNicknameService memberNicknameService = mock(MemberNicknameService.class);
        AuthTokenProvider authTokenProvider = mock(AuthTokenProvider.class);
        AuthService service = new AuthService(
                memberService,
                oauthIdentityService,
                memberNicknameService,
                authTokenProvider
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
        assertThat(result.getToken()).isEqualTo("nickname-token");
        assertThat(result.requiresNickname()).isTrue();
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
                mock(AuthTokenProvider.class)
        );

        // when & then
        assertThatThrownBy(() -> service.login(null)).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_OAUTH_USER)
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
                mock(AuthTokenProvider.class)
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

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser)).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.MEMBER_NOT_FOUND_FOR_OAUTH_IDENTITY)
        );
    }

    @Test
    @DisplayName("닉네임 토큰과 닉네임으로 member를 생성하고 access token을 발급한다")
    void completeNickname_success() {
        // given
        AuthTokenProvider authTokenProvider = mock(AuthTokenProvider.class);
        MemberNicknameService memberNicknameService = mock(MemberNicknameService.class);
        AuthService service = new AuthService(
                mock(MemberService.class),
                mock(OAuthIdentityService.class),
                memberNicknameService,
                authTokenProvider
        );
        OAuthUser oauthUser = oauthUser();
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getNickname()).thenReturn("octocat");
        when(member.getProfileImageUrl()).thenReturn(null);
        when(authTokenProvider.authenticateNickname("nickname-token")).thenReturn(oauthUser);
        when(
                memberNicknameService.completeNickname(
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
        String result = service.completeNickname(
                "nickname-token",
                "octocat"
        );

        // then
        assertThat(result).isEqualTo("access-token");
        verify(authTokenProvider).issue(authenticatedMember);
    }

    private OAuthUser oauthUser() {
        return OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                "https://example.com/avatar"
        );
    }
}

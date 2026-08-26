package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberNicknameServiceTest {

    @Test
    @DisplayName("닉네임을 입력하면 member와 OAuth identity 저장을 요청한다")
    void completeNickname_success() {
        // given
        MemberService memberService = mock(MemberService.class);
        OAuthIdentityService oauthIdentityService = mock(OAuthIdentityService.class);
        MemberNicknameService service = new MemberNicknameService(
                memberService,
                oauthIdentityService
        );
        OAuthUser oauthUser = oauthUser();
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(
                memberService.create(
                        "octocat",
                        "https://example.com/avatar"
                )
        ).thenReturn(member);

        // when
        Member result = service.completeNickname(
                oauthUser,
                "octocat"
        );

        // then
        assertThat(result).isSameAs(member);
        verify(oauthIdentityService).save(any(OAuthIdentity.class));
    }

    @Test
    @DisplayName("OAuth 사용자가 없으면 온보딩을 진행하지 않는다")
    void complete_failure_nullOAuthUser() {
        // given
        MemberService memberService = mock(MemberService.class);
        MemberNicknameService service = new MemberNicknameService(
                memberService,
                mock(OAuthIdentityService.class)
        );

        // when & then
        assertThatThrownBy(
                () -> service.completeNickname(
                        null,
                        "octocat"
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_OAUTH_USER)
        );
        verify(
                memberService,
                never()
        ).create(
                "octocat",
                null
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

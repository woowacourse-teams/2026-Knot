package com.knot.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuthIdentityTest {

    @Test
    @DisplayName("온보딩을 완료한 OAuth 사용자 identity를 생성한다")
    void create_success() {
        // given
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                "https://example.com/avatar"
        );

        // when
        OAuthIdentity identity = OAuthIdentity.create(
                oauthUser,
                1L
        );

        // then
        assertThat(identity.getProvider()).isEqualTo(OAuthProvider.GITHUB);
        assertThat(identity.getProviderUserId()).isEqualTo("42");
        assertThat(identity.getMemberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("member ID가 유효하지 않으면 identity를 생성하지 않는다")
    void create_failure_invalidMemberId() {
        // given
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );

        // when & then
        assertThatThrownBy(
                () -> OAuthIdentity.create(
                        oauthUser,
                        0L
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_OAUTH_USER)
        );
    }
}

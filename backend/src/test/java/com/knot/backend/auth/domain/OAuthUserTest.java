package com.knot.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuthUserTest {

    @Test
    @DisplayName("유효한 OAuth 사용자 정보를 생성한다")
    void create_success() {
        // when
        OAuthUser user = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                "https://example.com/avatar"
        );

        // then
        assertThat(user.getProvider()).isEqualTo(OAuthProvider.GITHUB);
        assertThat(user.getExternalId()).isEqualTo("42");
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/avatar");
    }

    @Test
    @DisplayName("OAuth provider가 없으면 커스텀 예외를 발생시킨다")
    void create_failure_nullProvider() {
        // when & then
        assertThatThrownBy(
                () -> OAuthUser.of(
                        null,
                        "42",
                        null
                )
        ).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("OAuth 외부 ID가 비어 있으면 커스텀 예외를 발생시킨다")
    void create_failure_blankExternalId() {
        // when & then
        assertThatThrownBy(
                () -> OAuthUser.of(
                        OAuthProvider.GITHUB,
                        " ",
                        null
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_OAUTH_USER)
        );
    }

    @Test
    @DisplayName("OAuth 외부 ID가 null이면 커스텀 예외를 발생시킨다")
    void create_failure_nullExternalId() {
        // when & then
        assertThatThrownBy(
                () -> OAuthUser.of(
                        OAuthProvider.GITHUB,
                        null,
                        null
                )
        ).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("OAuth 프로필 이미지 URL이 비어 있으면 커스텀 예외를 발생시킨다")
    void create_failure_blankProfileImageUrl() {
        // when & then
        assertThatThrownBy(
                () -> OAuthUser.of(
                        OAuthProvider.GITHUB,
                        "42",
                        " "
                )
        ).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("OAuth 프로필 이미지 URL이 길이 제한을 초과하면 커스텀 예외를 발생시킨다")
    void create_failure_overlongProfileImageUrl() {
        // when & then
        assertThatThrownBy(
                () -> OAuthUser.of(
                        OAuthProvider.GITHUB,
                        "42",
                        "a".repeat(501)
                )
        ).isInstanceOf(AuthException.class);
    }
}

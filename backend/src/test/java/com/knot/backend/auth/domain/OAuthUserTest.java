package com.knot.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuthUserTest {

    @Test
    @DisplayName("OAuth 사용자 ID가 유효하지 않으면 커스텀 예외를 발생시킨다")
    void create_failure_invalidExternalId() {
        // when & then
        assertThatThrownBy(() -> OAuthUser.of(0L, "octocat", null))
                .isInstanceOfSatisfying(
                        AuthException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_OAUTH_USER));
    }

    @Test
    @DisplayName("OAuth 닉네임이 비어 있으면 커스텀 예외를 발생시킨다")
    void create_failure_blankNickname() {
        // when & then
        assertThatThrownBy(() -> OAuthUser.of(42L, " ", null)).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("OAuth 닉네임이 20자를 초과하면 커스텀 예외를 발생시킨다")
    void create_failure_overlongNickname() {
        // when & then
        assertThatThrownBy(() -> OAuthUser.of(42L, "a".repeat(21), null))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("OAuth 프로필 이미지 URL이 비어 있으면 커스텀 예외를 발생시킨다")
    void create_failure_blankProfileImageUrl() {
        // when & then
        assertThatThrownBy(() -> OAuthUser.of(42L, "octocat", " "))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("OAuth 프로필 이미지 URL이 길이 제한을 초과하면 커스텀 예외를 발생시킨다")
    void create_failure_overlongProfileImageUrl() {
        // when & then
        assertThatThrownBy(() -> OAuthUser.of(42L, "octocat", "a".repeat(501)))
                .isInstanceOf(AuthException.class);
    }
}

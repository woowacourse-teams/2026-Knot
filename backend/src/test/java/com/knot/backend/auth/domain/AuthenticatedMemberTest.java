package com.knot.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthenticatedMemberTest {

    @Test
    @DisplayName("인증 사용자 ID가 유효하지 않으면 커스텀 예외를 발생시킨다")
    void create_failure_invalidMemberId() {
        // when & then
        assertThatThrownBy(
                () -> AuthenticatedMember.of(
                        0L,
                        42L,
                        "octocat",
                        null
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER)
        );
    }

    @Test
    @DisplayName("인증 사용자 GitHub ID가 유효하지 않으면 커스텀 예외를 발생시킨다")
    void create_failure_invalidGithubId() {
        // when & then
        assertThatThrownBy(
                () -> AuthenticatedMember.of(
                        1L,
                        0L,
                        "octocat",
                        null
                )
        ).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("인증 사용자 닉네임이 길이 제한을 초과하면 커스텀 예외를 발생시킨다")
    void create_failure_overlongNickname() {
        // when & then
        assertThatThrownBy(
                () -> AuthenticatedMember.of(
                        1L,
                        42L,
                        "a".repeat(40),
                        null
                )
        ).isInstanceOf(AuthException.class);
    }
}

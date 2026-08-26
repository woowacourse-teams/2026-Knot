package com.knot.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthenticatedMemberTest {

    @Test
    @DisplayName("유효한 인증 사용자 정보를 생성한다")
    void create_success() {
        // when
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                "octocat",
                "https://example.com/avatar"
        );

        // then
        assertThat(member.getMemberId()).isEqualTo(1L);
        assertThat(member.getNickname()).isEqualTo("octocat");
    }

    @Test
    @DisplayName("인증 사용자 ID가 유효하지 않으면 커스텀 예외를 발생시킨다")
    void create_failure_invalidMemberId() {
        // when & then
        assertThatThrownBy(
                () -> AuthenticatedMember.of(
                        0L,
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
    @DisplayName("인증 사용자 닉네임이 길이 제한을 초과하면 커스텀 예외를 발생시킨다")
    void create_failure_overlongNickname() {
        // when & then
        assertThatThrownBy(
                () -> AuthenticatedMember.of(
                        1L,
                        "a".repeat(21),
                        null
                )
        ).isInstanceOf(AuthException.class);
    }
}

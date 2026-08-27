package com.knot.backend.auth.application.dto.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthLoginResultTest {
    @Test
    @DisplayName("일반 access token 로그인 결과를 생성한다")
    void authenticated_success() {
        // given

        // when
        AuthLoginResult result = AuthLoginResult.authenticated("access-token");

        // then
        assertThat(result.token()).isEqualTo("access-token");
        assertThat(result.requiresNickname()).isFalse();
    }

    @Test
    @DisplayName("닉네임 설정이 필요한 로그인 결과를 생성한다")
    void nicknameSetupRequired_success() {
        // given

        // when
        AuthLoginResult result = AuthLoginResult.nicknameSetupRequired("nickname-token");

        // then
        assertThat(result.token()).isEqualTo("nickname-token");
        assertThat(result.requiresNickname()).isTrue();
    }

    @Test
    @DisplayName("일반 access token이 비어 있으면 인증 내부 오류를 발생시킨다")
    void authenticated_failure_blankToken() {
        // given

        // when
        Throwable thrown = catchThrowable(() -> AuthLoginResult.authenticated(" "));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR)
        );
    }

    @Test
    @DisplayName("닉네임 설정 token이 비어 있으면 인증 내부 오류를 발생시킨다")
    void nicknameSetupRequired_failure_blankToken() {
        // given

        // when
        Throwable thrown = catchThrowable(() -> AuthLoginResult.nicknameSetupRequired(null));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR)
        );
    }
}

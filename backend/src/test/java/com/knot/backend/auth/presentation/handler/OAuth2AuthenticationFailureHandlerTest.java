package com.knot.backend.auth.presentation.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.global.config.OAuth2LoginProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2AuthenticationFailureHandlerTest {

    @Test
    @DisplayName("OAuth 인증 실패를 로그인 오류 화면으로 redirect한다")
    void onAuthenticationFailure_redirectsToLogin() throws Exception {
        // given
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        loginProperties.setFailureRedirectUri("/login?error=oauth2");
        OAuth2AuthenticationFailureHandler handler =
                new OAuth2AuthenticationFailureHandler(loginProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception =
                new OAuth2AuthenticationException(new OAuth2Error("oauth_error"));

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=oauth2");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("OAuth 실패 redirect 설정이 비어 있으면 설정 예외를 발생시킨다")
    void create_failure_blankRedirectUri() {
        // given
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        loginProperties.setFailureRedirectUri(" ");

        // when & then
        assertThatThrownBy(() -> new OAuth2AuthenticationFailureHandler(loginProperties))
                .isInstanceOfSatisfying(
                        AuthException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(AuthErrorCode.OAUTH_CONFIGURATION_INVALID));
    }
}

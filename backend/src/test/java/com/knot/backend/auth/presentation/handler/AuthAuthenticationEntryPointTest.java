package com.knot.backend.auth.presentation.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import tools.jackson.databind.ObjectMapper;

class AuthAuthenticationEntryPointTest {

    @Test
    @DisplayName("인증되지 않은 요청에 구조화된 401 응답을 반환한다")
    void commence_failure_unauthenticated() throws Exception {
        // given
        AuthAuthenticationEntryPoint entryPoint = new AuthAuthenticationEntryPoint(new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        entryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new OAuth2AuthenticationException(new OAuth2Error("unauthorized"))
        );

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"message\":\"인증이 필요합니다\"");
    }
}

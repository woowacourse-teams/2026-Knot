package com.knot.backend.auth.presentation.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.presentation.AuthCookieManager;
import com.knot.backend.global.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class JwtLogoutHandlerTest {

    @Test
    @DisplayName("로그아웃 시 JWT 쿠키를 만료시킨다")
    void logout_success() {
        // given
        JwtProperties properties = new JwtProperties();
        properties.setCookieName("KNOT_ACCESS_TOKEN");
        properties.setExpiration(Duration.ofHours(1));
        properties.setSecure(false);
        AuthService authService = mock(AuthService.class);
        JwtLogoutHandler handler = new JwtLogoutHandler(
                new AuthCookieManager(properties),
                authService,
                new ObjectMapper()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        "KNOT_REFRESH_TOKEN",
                        "refresh-token"
                )
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.logout(
                request,
                response,
                null
        );

        // then
        verify(authService).logout("refresh-token");
        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).anySatisfy(
                cookie -> assertThat(cookie).contains(
                        "KNOT_ACCESS_TOKEN=",
                        "Max-Age=0",
                        "Path=/",
                        "HttpOnly",
                        "SameSite=Lax"
                )
        );
        assertThat(cookies).anySatisfy(
                cookie -> assertThat(cookie).contains(
                        "KNOT_REFRESH_TOKEN=",
                        "Max-Age=0",
                        "Path=/",
                        "HttpOnly",
                        "SameSite=Lax"
                )
        );
        assertThat(cookies).anySatisfy(
                cookie -> assertThat(cookie).contains(
                        "KNOT_NICKNAME_TOKEN=",
                        "Max-Age=0",
                        "Path=/",
                        "HttpOnly",
                        "SameSite=Lax"
                )
        );
    }

    @Test
    @DisplayName("세션 폐기에 실패하면 인증 쿠키를 지우지 않고 내부 오류를 반환한다")
    void logout_failure_sessionStorage() throws Exception {
        // given
        JwtProperties properties = new JwtProperties();
        properties.setSecure(false);
        AuthService authService = mock(AuthService.class);
        doThrow(new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR)).when(authService)
                .logout("refresh-token");
        JwtLogoutHandler handler = new JwtLogoutHandler(
                new AuthCookieManager(properties),
                authService,
                new ObjectMapper()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        "KNOT_REFRESH_TOKEN",
                        "refresh-token"
                )
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.logout(
                request,
                response,
                null
        );

        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("AUTHENTICATION_INTERNAL_ERROR");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }
}

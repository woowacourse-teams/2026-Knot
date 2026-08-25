package com.knot.backend.auth.presentation.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.global.config.JwtProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtLogoutHandlerTest {

    @Test
    @DisplayName("로그아웃 시 JWT 쿠키를 만료시킨다")
    void logout_success() {
        // given
        JwtProperties properties = new JwtProperties();
        properties.setCookieName("KNOT_ACCESS_TOKEN");
        properties.setExpiration(Duration.ofHours(1));
        properties.setSecure(false);
        JwtLogoutHandler handler = new JwtLogoutHandler(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.logout(
                new MockHttpServletRequest(),
                response,
                null
        );

        // then
        assertThat(response.getHeader("Set-Cookie")).contains("KNOT_ACCESS_TOKEN=")
                .contains("Max-Age=0")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }
}

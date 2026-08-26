package com.knot.backend.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.global.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieManagerTest {

    @Test
    @DisplayName("access token을 공통 쿠키 정책으로 발급한다")
    void addAccessToken_success() {
        // given
        JwtProperties properties = properties();
        AuthCookieManager manager = new AuthCookieManager(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        manager.addAccessToken(
                response,
                "access-token"
        );

        // then
        Cookie cookie = response.getCookie("KNOT_ACCESS_TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo("access-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(3600);
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Lax");
    }

    @Test
    @DisplayName("nickname token을 공통 쿠키 정책으로 발급한다")
    void addNicknameToken_success() {
        // given
        JwtProperties properties = properties();
        AuthCookieManager manager = new AuthCookieManager(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        manager.addNicknameToken(
                response,
                "nickname-token"
        );

        // then
        Cookie cookie = response.getCookie("KNOT_NICKNAME_TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo("nickname-token");
        assertThat(cookie.getMaxAge()).isEqualTo(600);
    }

    @Test
    @DisplayName("access token 쿠키를 만료시킨다")
    void expireAccessToken_success() {
        // given
        AuthCookieManager manager = new AuthCookieManager(properties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        manager.expireAccessToken(response);

        // then
        assertThat(
                response.getCookie("KNOT_ACCESS_TOKEN")
                        .getMaxAge()
        ).isZero();
    }

    @Test
    @DisplayName("nickname token 쿠키를 만료시킨다")
    void expireNicknameToken_success() {
        // given
        AuthCookieManager manager = new AuthCookieManager(properties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        manager.expireNicknameToken(response);

        // then
        assertThat(
                response.getCookie("KNOT_NICKNAME_TOKEN")
                        .getMaxAge()
        ).isZero();
    }

    private JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setCookieName("KNOT_ACCESS_TOKEN");
        properties.setNicknameCookieName("KNOT_NICKNAME_TOKEN");
        properties.setExpiration(Duration.ofHours(1));
        properties.setNicknameTokenExpiration(Duration.ofMinutes(10));
        properties.setSecure(false);
        return properties;
    }
}

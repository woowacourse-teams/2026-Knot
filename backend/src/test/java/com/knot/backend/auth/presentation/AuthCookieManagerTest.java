package com.knot.backend.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.knot.backend.auth.domain.AuthException;

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

    @Test
    @DisplayName("refresh 쿠키는 계산된 남은 수명과 보안 속성을 사용한다")
    void addRefreshToken_success() {
        // given
        JwtProperties properties = properties();
        properties.setSecure(true);
        properties.setRefreshCookieName("__Host-KNOT_REFRESH_TOKEN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        new AuthCookieManager(properties).addRefreshToken(response, "refresh-token", Duration.ofHours(2));

        // then
        Cookie cookie = response.getCookie("__Host-KNOT_REFRESH_TOKEN");
        assertThat(cookie.getValue()).isEqualTo("refresh-token");
        assertThat(cookie.getMaxAge()).isEqualTo(7200);
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Lax");
    }

    @Test
    @DisplayName("빈 refresh나 만료된 수명으로는 쿠키를 발급하지 않는다")
    void addRefreshToken_failure_invalidCredential() {
        // given
        AuthCookieManager manager = new AuthCookieManager(properties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when & then
        assertThatThrownBy(() -> manager.addRefreshToken(response, " ", Duration.ofDays(7)))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> manager.addRefreshToken(response, "refresh", Duration.ZERO))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> manager.addRefreshToken(response, "refresh", null))
                .isInstanceOf(AuthException.class);
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    @DisplayName("Secure 없이 Host 접두사 refresh 쿠키를 발급할 수 없다")
    void addRefreshToken_failure_insecureHostCookie() {
        // given
        JwtProperties properties = properties();
        properties.setRefreshCookieName("__Host-KNOT_REFRESH_TOKEN");

        // when & then
        assertThatThrownBy(() -> new AuthCookieManager(properties).addRefreshToken(
                new MockHttpServletResponse(), "refresh", Duration.ofDays(7))).isInstanceOf(AuthException.class);
    }
}

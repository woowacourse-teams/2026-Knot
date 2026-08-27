package com.knot.backend.auth.presentation;

import com.knot.backend.global.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieManager {
    private final JwtProperties jwtProperties;

    public AuthCookieManager(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public void addAccessToken(
            HttpServletResponse response,
            String token
    ) {
        addCookie(
                response,
                jwtProperties.getCookieName(),
                token,
                jwtProperties.getExpiration()
        );
    }

    public void addNicknameToken(
            HttpServletResponse response,
            String token
    ) {
        addCookie(
                response,
                jwtProperties.getNicknameCookieName(),
                token,
                jwtProperties.getNicknameTokenExpiration()
        );
    }

    public void expireAccessToken(HttpServletResponse response) {
        expireCookie(
                response,
                jwtProperties.getCookieName()
        );
    }

    public void expireNicknameToken(HttpServletResponse response) {
        expireCookie(
                response,
                jwtProperties.getNicknameCookieName()
        );
    }

    private void expireCookie(
            HttpServletResponse response,
            String name
    ) {
        addCookie(
                response,
                name,
                "",
                Duration.ZERO
        );
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            Duration maxAge
    ) {
        ResponseCookie cookie = ResponseCookie.from(
                name,
                value
        )
                .httpOnly(true)
                .secure(jwtProperties.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }
}

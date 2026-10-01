package com.knot.backend.auth.presentation;

import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    public void addRefreshToken(
            HttpServletResponse response,
            String token,
            Duration maxAge
    ) {
        String name = jwtProperties.getRefreshCookieName();
        if (token == null || token.isBlank() || maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (name == null || name.isBlank() || (name.startsWith("__Host-") && !jwtProperties.isSecure())) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        addCookie(
                response,
                name,
                token,
                maxAge
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

    public void expireLoginCookies(HttpServletResponse response) {
        Set<String> names = new LinkedHashSet<>(
                List.of(
                        jwtProperties.getCookieName(),
                        jwtProperties.getRefreshCookieName(),
                        jwtProperties.getNicknameCookieName(),
                        "KNOT_ACCESS_TOKEN",
                        "KNOT_REFRESH_TOKEN",
                        "KNOT_NICKNAME_TOKEN",
                        "__Host-KNOT_ACCESS_TOKEN",
                        "__Host-KNOT_REFRESH_TOKEN"
                )
        );
        for (String name : names) {
            expireCookie(
                    response,
                    name
            );
        }
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

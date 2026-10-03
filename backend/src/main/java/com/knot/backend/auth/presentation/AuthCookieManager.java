package com.knot.backend.auth.presentation;

import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
                cookieNameForSecurity(jwtProperties.getCookieName()),
                token,
                jwtProperties.getExpiration()
        );
    }

    public void addRefreshToken(
            HttpServletResponse response,
            String token,
            Duration maxAge
    ) {
        String name = cookieNameForSecurity(jwtProperties.getRefreshCookieName());
        if (token == null || token.isBlank() || maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        addCookie(
                response,
                name,
                token,
                maxAge
        );
    }

    public String findRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String refreshCookieName = cookieNameForSecurity(jwtProperties.getRefreshCookieName());
        String refreshToken = null;
        boolean found = false;
        for (Cookie cookie : cookies) {
            if (!refreshCookieName.equals(cookie.getName())) {
                continue;
            }
            if (found) {
                throw new AuthException(AuthErrorCode.INVALID_JWT);
            }
            found = true;
            refreshToken = cookie.getValue();
        }
        return refreshToken;
    }

    public void addNicknameToken(
            HttpServletResponse response,
            String token
    ) {
        addCookie(
                response,
                cookieNameForSecurity(jwtProperties.getNicknameCookieName()),
                token,
                jwtProperties.getNicknameTokenExpiration()
        );
    }

    public void expireLoginCookies(HttpServletResponse response) {
        Set<String> names = new LinkedHashSet<>(
                List.of(
                        cookieNameForSecurity(jwtProperties.getCookieName()),
                        cookieNameForSecurity(jwtProperties.getRefreshCookieName()),
                        cookieNameForSecurity(jwtProperties.getNicknameCookieName()),
                        cookieNameForSecurity("KNOT_ACCESS_TOKEN"),
                        cookieNameForSecurity("KNOT_REFRESH_TOKEN"),
                        cookieNameForSecurity("KNOT_NICKNAME_TOKEN"),
                        cookieNameForSecurity("__Host-KNOT_ACCESS_TOKEN"),
                        cookieNameForSecurity("__Host-KNOT_REFRESH_TOKEN")
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
                cookieNameForSecurity(jwtProperties.getCookieName())
        );
    }

    public void expireNicknameToken(HttpServletResponse response) {
        expireCookie(
                response,
                cookieNameForSecurity(jwtProperties.getNicknameCookieName())
        );
    }

    private String cookieNameForSecurity(String name) {
        if (name == null || name.isBlank()) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        if (jwtProperties.isSecure()) {
            return name;
        }
        if (name.startsWith("__Host-")) {
            return name.substring("__Host-".length());
        }
        if (name.startsWith("__Secure-")) {
            return name.substring("__Secure-".length());
        }
        return name;
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

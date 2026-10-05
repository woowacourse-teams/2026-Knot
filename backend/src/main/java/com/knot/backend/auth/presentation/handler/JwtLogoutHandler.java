package com.knot.backend.auth.presentation.handler;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.presentation.AuthCookieManager;
import com.knot.backend.global.response.ErrorResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {
    private static final Logger log = LoggerFactory.getLogger(JwtLogoutHandler.class);

    private final AuthCookieManager authCookieManager;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        try {
            authService.logout(refreshToken(request));
        } catch (RuntimeException exception) {
            log.error(
                    "로그아웃 중 인증 세션 폐기에 실패했습니다",
                    exception
            );
            request.setAttribute(
                    AuthLogoutSuccessHandler.LOGOUT_FAILURE_ATTRIBUTE,
                    true
            );
            writeInternalError(response);
            return;
        }

        authCookieManager.expireLoginCookies(response);
    }

    private String refreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String refreshCookieName = authCookieManager.refreshTokenCookieName();
        return Arrays.stream(cookies)
                .filter(cookie -> refreshCookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void writeInternalError(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            objectMapper.writeValue(
                    response.getWriter(),
                    new ErrorResponse(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR)
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}

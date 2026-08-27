package com.knot.backend.auth.presentation.handler;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.global.config.OAuth2LoginProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    private final OAuth2LoginProperties loginProperties;

    public OAuth2AuthenticationFailureHandler(OAuth2LoginProperties loginProperties) {
        if (loginProperties == null || loginProperties.getFailureRedirectUri() == null
                || loginProperties.getFailureRedirectUri()
                        .isBlank()) {
            throw new AuthException(AuthErrorCode.OAUTH_CONFIGURATION_INVALID);
        }
        this.loginProperties = loginProperties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String exceptionType = exception == null
                ? "unknown"
                : exception.getClass()
                        .getSimpleName();
        log.warn(
                "OAuth 인증 실패: type={}",
                exceptionType
        );
        handleFailure(
                request,
                response
        );
    }

    public void handleFailure(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        clearAuthentication(request);
        response.sendRedirect(loginProperties.getFailureRedirectUri());
    }

    public void clearAuthentication(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}

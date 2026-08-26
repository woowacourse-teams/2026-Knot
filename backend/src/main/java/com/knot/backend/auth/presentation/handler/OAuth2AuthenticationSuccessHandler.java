package com.knot.backend.auth.presentation.handler;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.AuthLoginResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.infrastructure.github.GithubOAuth2User;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.global.config.OAuth2LoginProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory
            .getLogger(OAuth2AuthenticationSuccessHandler.class);
    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final OAuth2LoginProperties loginProperties;

    public OAuth2AuthenticationSuccessHandler(
            AuthService authService,
            JwtProperties jwtProperties,
            OAuth2LoginProperties loginProperties
    ) {
        if (loginProperties == null || loginProperties.getSuccessRedirectUri() == null
                || loginProperties.getSuccessRedirectUri()
                        .isBlank()
                || loginProperties.getNicknameRedirectUri() == null
                || loginProperties.getNicknameRedirectUri()
                        .isBlank()
                || loginProperties.getFailureRedirectUri() == null
                || loginProperties.getFailureRedirectUri()
                        .isBlank()) {
            throw new AuthException(AuthErrorCode.OAUTH_CONFIGURATION_INVALID);
        }
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.loginProperties = loginProperties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            GithubOAuth2User githubUser = getGithubUser(authentication);
            AuthLoginResult result = authService.login(githubUser.getOAuthUser());

            if (result.requiresNickname()) {
                addCookie(
                        response,
                        jwtProperties.getNicknameCookieName(),
                        result.getToken(),
                        jwtProperties.getNicknameTokenExpiration()
                );
                clearAuthentication(request);
                response.sendRedirect(loginProperties.getNicknameRedirectUri());
                return;
            }

            addCookie(
                    response,
                    jwtProperties.getCookieName(),
                    result.getToken(),
                    jwtProperties.getExpiration()
            );
            clearAuthentication(request);
            response.sendRedirect(loginProperties.getSuccessRedirectUri());
        } catch (AuthException exception) {
            log.warn(
                    "OAuth 인증 처리 실패: errorCode={}",
                    exception.getErrorCode()
            );
            handleFailure(
                    request,
                    response
            );
        } catch (RuntimeException exception) {
            log.error(
                    "OAuth 인증 처리 중 예기치 않은 오류가 발생했습니다.",
                    exception
            );
            handleFailure(
                    request,
                    response
            );
        }
    }

    private void handleFailure(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        clearAuthentication(request);
        response.sendRedirect(loginProperties.getFailureRedirectUri());
    }

    private void clearAuthentication(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
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

    private GithubOAuth2User getGithubUser(Authentication authentication) {
        if (authentication == null) {
            throw new AuthException(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }

        try {
            GithubOAuth2User githubUser = (GithubOAuth2User) authentication.getPrincipal();
            if (githubUser == null) {
                throw new AuthException(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED);
            }
            return githubUser;
        } catch (ClassCastException exception) {
            throw new AuthException(
                    AuthErrorCode.OAUTH_AUTHENTICATION_FAILED,
                    exception
            );
        }
    }
}

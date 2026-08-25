package com.knot.backend.auth.presentation.handler;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.infrastructure.github.GithubOAuth2User;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.global.config.OAuth2LoginProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final OAuth2LoginProperties loginProperties;
    private final OAuth2AuthenticationFailureHandler failureHandler;

    public OAuth2AuthenticationSuccessHandler(
            AuthService authService,
            JwtProperties jwtProperties,
            OAuth2LoginProperties loginProperties,
            OAuth2AuthenticationFailureHandler failureHandler
    ) {
        if (loginProperties == null || loginProperties.getSuccessRedirectUri() == null
                || loginProperties.getSuccessRedirectUri()
                        .isBlank()) {
            throw new AuthException(AuthErrorCode.OAUTH_CONFIGURATION_INVALID);
        }
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.loginProperties = loginProperties;
        this.failureHandler = failureHandler;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            GithubOAuth2User githubUser = getGithubUser(authentication);
            String token = authService.login(githubUser.getOAuthUser());
            ResponseCookie cookie = ResponseCookie.from(
                    jwtProperties.getCookieName(),
                    token
            )
                    .httpOnly(true)
                    .secure(jwtProperties.isSecure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(jwtProperties.getExpiration())
                    .build();
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    cookie.toString()
            );
            failureHandler.clearAuthentication(request);
            response.sendRedirect(loginProperties.getSuccessRedirectUri());
        } catch (AuthException exception) {
            failureHandler.handleFailure(
                    request,
                    response
            );
        } catch (RuntimeException exception) {
            failureHandler.handleFailure(
                    request,
                    response
            );
        }
    }

    private GithubOAuth2User getGithubUser(Authentication authentication) {
        if (authentication == null) {
            throw new AuthException(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }

        try {
            GithubOAuth2User githubUser = GithubOAuth2User.class
                    .cast(authentication.getPrincipal());
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

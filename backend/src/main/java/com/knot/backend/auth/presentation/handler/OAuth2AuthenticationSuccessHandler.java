package com.knot.backend.auth.presentation.handler;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.dto.result.AuthLoginResult;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로그인에 성공한 사용자를 토큰과 함께 프론트로 돌려보낸다.
 *
 * 토큰은 쿠키가 아니라 리다이렉트 URL의 **프래그먼트**로 넘긴다(기획서 5.1, 로드맵 Q14). `#` 뒤는 서버로 전송되지 않아
 * 액세스 로그·`Referer`에 남지 않으며, 받은 SPA가 `history.replaceState`로 주소창에서 지운다.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    private final AuthService authService;
    private final OAuth2LoginProperties loginProperties;
    private final JwtProperties jwtProperties;

    public OAuth2AuthenticationSuccessHandler(
            AuthService authService,
            OAuth2LoginProperties loginProperties,
            JwtProperties jwtProperties
    ) {
        if (loginProperties == null) {
            throw new AuthException(AuthErrorCode.OAUTH_CONFIGURATION_INVALID);
        }
        validateRedirectUri(loginProperties.getSuccessRedirectUri());
        validateRedirectUri(loginProperties.getNicknameRedirectUri());
        validateRedirectUri(loginProperties.getFailureRedirectUri());
        this.authService = authService;
        this.loginProperties = loginProperties;
        this.jwtProperties = jwtProperties;
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

            String redirectUri = result.requiresNickname()
                    ? onboardingRedirectUri(result.token())
                    : accessRedirectUri(result.token());

            clearAuthentication(request);
            response.sendRedirect(redirectUri);
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

    private String accessRedirectUri(String accessToken) {
        return loginProperties.getSuccessRedirectUri() + "#access_token=" + accessToken + "&token_type="
                + BEARER_TOKEN_TYPE + "&expires_in=" + jwtProperties.getExpiration()
                        .toSeconds();
    }

    private String onboardingRedirectUri(String onboardingToken) {
        return loginProperties.getNicknameRedirectUri() + "#onboarding_token=" + onboardingToken + "&expires_in="
                + jwtProperties.getNicknameTokenExpiration()
                        .toSeconds();
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

    /**
     * 프래그먼트를 이어 붙이므로 설정 값에 이미 `#`이 있으면 토큰이 실리지 않는다. 배포 설정 실수를 뜨는 시점(빈 생성)에 드러내려고
     * 여기서 막는다.
     */
    private void validateRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank() || redirectUri.contains("#")) {
            throw new AuthException(AuthErrorCode.OAUTH_CONFIGURATION_INVALID);
        }
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

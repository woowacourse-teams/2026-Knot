package com.knot.backend.auth.presentation.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.AuthLoginResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.infrastructure.github.GithubOAuth2User;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.global.config.OAuth2LoginProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuth2AuthenticationSuccessHandlerTest {

    @Test
    @DisplayName("OAuth 인증 성공 시 JWT 쿠키를 발급하고 설정된 URI로 redirect한다")
    void onAuthenticationSuccess_success_issuesCookieAndRedirects() throws Exception {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        loginProperties.setSuccessRedirectUri("/auth/me");
        OAuth2AuthenticationFailureHandler failureHandler = new OAuth2AuthenticationFailureHandler(
                loginProperties
        );
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                jwtProperties,
                loginProperties,
                failureHandler
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );
        OAuth2User delegate = mock(OAuth2User.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(delegate)
                .getAuthorities();
        when(delegate.getAttributes()).thenReturn(
                Map.of(
                        "id",
                        42L
                )
        );
        GithubOAuth2User githubUser = GithubOAuth2User.of(
                oauthUser,
                delegate
        );
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(githubUser);
        when(authService.login(oauthUser)).thenReturn(AuthLoginResult.authenticated("jwt-token"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo("/auth/me");
        Cookie cookie = response.getCookie("KNOT_ACCESS_TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo("jwt-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(3600);
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Lax");
    }

    @Test
    @DisplayName("처음 OAuth 인증한 사용자는 닉네임 쿠키를 발급하고 닉네임 페이지로 redirect한다")
    void onAuthenticationSuccess_nickname_redirectsToNickname() throws Exception {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        loginProperties.setNicknameRedirectUri("/nickname");
        OAuth2AuthenticationFailureHandler failureHandler = new OAuth2AuthenticationFailureHandler(
                loginProperties
        );
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                jwtProperties,
                loginProperties,
                failureHandler
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );
        OAuth2User delegate = mock(OAuth2User.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(delegate)
                .getAuthorities();
        when(delegate.getAttributes()).thenReturn(
                Map.of(
                        "id",
                        42L
                )
        );
        GithubOAuth2User githubUser = GithubOAuth2User.of(
                oauthUser,
                delegate
        );
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(githubUser);
        when(authService.login(oauthUser)).thenReturn(AuthLoginResult.nickname("nickname-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication
        );

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo("/nickname");
        Cookie cookie = response.getCookie("KNOT_NICKNAME_TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo("nickname-token");
        assertThat(response.getCookie("KNOT_ACCESS_TOKEN")).isNull();
    }

    @Test
    @DisplayName("OAuth 인증 주체가 GitHub 사용자가 아니면 커스텀 인증 예외를 발생시킨다")
    void onAuthenticationSuccess_failure_invalidPrincipal() throws Exception {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        OAuth2AuthenticationFailureHandler failureHandler = new OAuth2AuthenticationFailureHandler(
                loginProperties
        );
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                jwtProperties,
                loginProperties,
                failureHandler
        );
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("invalid-principal");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication
        );

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=oauth2");
        verify(
                authService,
                never()
        ).login(any());
    }

    @Test
    @DisplayName("로그인 애플리케이션 서비스 실패를 안전한 오류 redirect로 변환한다")
    void onAuthenticationSuccess_failure_applicationError() throws Exception {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        OAuth2AuthenticationFailureHandler failureHandler = new OAuth2AuthenticationFailureHandler(
                loginProperties
        );
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                jwtProperties,
                loginProperties,
                failureHandler
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );
        OAuth2User delegate = mock(OAuth2User.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(delegate)
                .getAuthorities();
        when(delegate.getAttributes()).thenReturn(
                Map.of(
                        "id",
                        42L
                )
        );
        GithubOAuth2User githubUser = GithubOAuth2User.of(
                oauthUser,
                delegate
        );
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(githubUser);
        when(authService.login(oauthUser))
                .thenThrow(new AuthException(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication
        );

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=oauth2");
    }

    @Test
    @DisplayName("OAuth 성공 redirect 설정이 비어 있으면 설정 예외를 발생시킨다")
    void create_failure_blankSuccessRedirectUri() {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        loginProperties.setSuccessRedirectUri(" ");
        OAuth2AuthenticationFailureHandler failureHandler = new OAuth2AuthenticationFailureHandler(
                loginProperties
        );

        // when & then
        assertThatThrownBy(
                () -> new OAuth2AuthenticationSuccessHandler(
                        authService,
                        jwtProperties,
                        loginProperties,
                        failureHandler
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.OAUTH_CONFIGURATION_INVALID)
        );
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setCookieName("KNOT_ACCESS_TOKEN");
        properties.setExpiration(Duration.ofHours(1));
        properties.setSecure(false);
        return properties;
    }
}

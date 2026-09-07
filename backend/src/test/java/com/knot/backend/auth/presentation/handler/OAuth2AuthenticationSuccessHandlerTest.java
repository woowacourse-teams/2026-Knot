package com.knot.backend.auth.presentation.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.dto.result.AuthLoginResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.infrastructure.github.GithubOAuth2User;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.global.config.OAuth2LoginProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuth2AuthenticationSuccessHandlerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("OAuth 인증 성공 시 액세스 토큰을 프래그먼트에 실어 설정된 URI로 redirect한다")
    void onAuthenticationSuccess_success_redirectsWithAccessTokenFragment() throws Exception {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        loginProperties.setSuccessRedirectUri("/api/v1/auth/me");
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                loginProperties,
                jwtProperties
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
        request.getSession();
        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        // then
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/api/v1/auth/me#access_token=jwt-token&token_type=Bearer&expires_in=3600");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(request.getSession(false)).isNull();
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).isNull();
    }

    @Test
    @DisplayName("처음 OAuth 인증한 사용자는 온보딩 토큰을 프래그먼트로 받고 설정 화면으로 이동한다")
    void onAuthenticationSuccess_success_nicknameSetupRequired() throws Exception {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        loginProperties.setNicknameRedirectUri("/nickname");
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                loginProperties,
                jwtProperties
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
        when(authService.login(oauthUser)).thenReturn(AuthLoginResult.nicknameSetupRequired("nickname-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication
        );

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo("/nickname#onboarding_token=nickname-token&expires_in=600");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    @DisplayName("OAuth 인증 주체가 GitHub 사용자가 아니면 커스텀 인증 예외를 발생시킨다")
    void onAuthenticationSuccess_failure_invalidPrincipal() throws Exception {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                loginProperties,
                jwtProperties
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
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                loginProperties,
                jwtProperties
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
        when(authService.login(oauthUser)).thenThrow(new AuthException(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED));
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

        // when
        Throwable thrown = catchThrowable(
                () -> new OAuth2AuthenticationSuccessHandler(
                        authService,
                        loginProperties,
                        jwtProperties
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.OAUTH_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("OAuth 실패 redirect 설정이 비어 있으면 설정 예외를 발생시킨다")
    void create_failure_blankFailureRedirectUri() {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        loginProperties.setFailureRedirectUri(" ");

        // when
        Throwable thrown = catchThrowable(
                () -> new OAuth2AuthenticationSuccessHandler(
                        authService,
                        loginProperties,
                        jwtProperties
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.OAUTH_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("redirect 설정에 프래그먼트가 들어 있으면 설정 예외를 발생시킨다")
    void create_failure_redirectUriWithFragment() {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = jwtProperties();
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        loginProperties.setSuccessRedirectUri("https://knoted.kr/#already");

        // when
        Throwable thrown = catchThrowable(
                () -> new OAuth2AuthenticationSuccessHandler(
                        authService,
                        loginProperties,
                        jwtProperties
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.OAUTH_CONFIGURATION_INVALID)
        );
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setExpiration(Duration.ofHours(1));
        properties.setNicknameTokenExpiration(Duration.ofMinutes(10));
        return properties;
    }
}

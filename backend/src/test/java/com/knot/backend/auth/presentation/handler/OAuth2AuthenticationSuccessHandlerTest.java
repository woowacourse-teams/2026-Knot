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
import com.knot.backend.auth.application.DeviceAuthService;
import com.knot.backend.auth.application.dto.result.AuthLoginResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.DeviceLoginRequest;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.infrastructure.github.GithubOAuth2User;
import com.knot.backend.auth.infrastructure.oauth.DesktopOAuth2AuthorizationRequestResolver;
import com.knot.backend.auth.infrastructure.oauth.StashingAuthorizationRequestRepository;
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
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
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
                mock(DeviceAuthService.class),
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
                mock(DeviceAuthService.class),
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
                mock(DeviceAuthService.class),
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
                mock(DeviceAuthService.class),
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
                        mock(DeviceAuthService.class),
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
                        mock(DeviceAuthService.class),
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
                        mock(DeviceAuthService.class),
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
    @DisplayName("데스크톱 로그인(client=desktop)이면 토큰 대신 일회용 코드를 loopback 주소로 보낸다")
    void onAuthenticationSuccess_success_desktopRedirectsCodeToLoopback() throws Exception {
        // given
        AuthService authService = mock(AuthService.class);
        DeviceAuthService deviceAuthService = mock(DeviceAuthService.class);
        OAuth2LoginProperties loginProperties = new OAuth2LoginProperties();
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                deviceAuthService,
                loginProperties,
                jwtProperties()
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );
        Authentication authentication = githubAuthentication(oauthUser);
        String codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        when(
                deviceAuthService.issueCode(
                        oauthUser,
                        codeChallenge
                )
        ).thenReturn("device-code");
        MockHttpServletRequest request = requestWithDeviceLogin(
                DeviceLoginRequest.of(
                        codeChallenge,
                        "S256",
                        "app-state",
                        "loopback:49152"
                )
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        // then
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://127.0.0.1:49152/callback?code=device-code&state=app-state");
        verify(
                authService,
                never()
        ).login(any());
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("데스크톱 로그인이 실패하면 같은 복귀 주소로 오류를 보내 앱의 대기를 풀어 준다")
    void onAuthenticationSuccess_failure_desktopRedirectsErrorToDeepLink() throws Exception {
        // given
        AuthService authService = mock(AuthService.class);
        DeviceAuthService deviceAuthService = mock(DeviceAuthService.class);
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authService,
                deviceAuthService,
                new OAuth2LoginProperties(),
                jwtProperties()
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );
        when(
                deviceAuthService.issueCode(
                        any(),
                        any()
                )
        ).thenThrow(new AuthException(AuthErrorCode.OAUTH_AUTHENTICATION_FAILED));
        MockHttpServletRequest request = requestWithDeviceLogin(
                DeviceLoginRequest.of(
                        "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                        "S256",
                        "app-state",
                        "deeplink"
                )
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(
                request,
                response,
                githubAuthentication(oauthUser)
        );

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo("knot://auth/callback?error=oauth2&state=app-state");
    }

    private Authentication githubAuthentication(OAuthUser oauthUser) {
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
        return authentication;
    }

    /**
     * 콜백 요청을 흉내 낸다. 인가 요청을 세션에 저장했다가 콜백에서 지우면 저장소가 요청 attribute에 남기고, 핸들러는 거기서
     * 데스크톱 로그인 파라미터를 읽는다.
     */
    private MockHttpServletRequest requestWithDeviceLogin(DeviceLoginRequest deviceLogin) {
        StashingAuthorizationRequestRepository repository = new StashingAuthorizationRequestRepository();
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId("client")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .redirectUri("http://localhost:8080/login/oauth2/code/github")
                .state("spring-state")
                .attributes(
                        attributes -> attributes.put(
                                DesktopOAuth2AuthorizationRequestResolver.DEVICE_LOGIN_ATTRIBUTE,
                                deviceLogin
                        )
                )
                .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(
                "state",
                "spring-state"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest,
                request,
                response
        );
        repository.removeAuthorizationRequest(
                request,
                response
        );
        return request;
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setExpiration(Duration.ofHours(1));
        properties.setNicknameTokenExpiration(Duration.ofMinutes(10));
        return properties;
    }
}

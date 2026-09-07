package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.knot.backend.auth.infrastructure.github.GithubOAuth2UserService;
import com.knot.backend.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.knot.backend.auth.presentation.handler.AuthAccessDeniedHandler;
import com.knot.backend.auth.presentation.handler.AuthAuthenticationEntryPoint;
import com.knot.backend.auth.presentation.handler.OAuth2AuthenticationFailureHandler;
import com.knot.backend.auth.presentation.handler.OAuth2AuthenticationSuccessHandler;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class SecurityConfigTest {

    @Test
    @DisplayName("CORS 설정은 개발 Origin과 마지막으로 본 워크스페이스 갱신에 필요한 PUT을 허용한다")
    void corsConfigurationSource_success_allowsDevelopmentOriginsAndPutMethod() {
        // given
        SecurityConfig securityConfig = new SecurityConfig(
                mock(GithubOAuth2UserService.class),
                mock(JwtAuthenticationFilter.class),
                mock(OAuth2AuthenticationSuccessHandler.class),
                mock(OAuth2AuthenticationFailureHandler.class),
                mock(AuthAuthenticationEntryPoint.class),
                mock(AuthAccessDeniedHandler.class),
                corsProperties(),
                new ApiDocumentationProperties()
        );

        // when
        UrlBasedCorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.PUT.name(),
                "/api/v1/members/me/last-viewed-workspace"
        );
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        // then
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedMethods()).contains(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.OPTIONS.name()
        );
        assertThat(configuration.getAllowedOrigins()).containsExactly(
                "https://dev.knoted.kr",
                "http://localhost:3000"
        );
    }

    @Test
    @DisplayName("CORS 설정은 Authorization 헤더를 허용하고 자격증명 전송을 열지 않는다")
    void corsConfigurationSource_success_allowsAuthorizationHeaderWithoutCredentials() {
        // given
        SecurityConfig securityConfig = new SecurityConfig(
                mock(GithubOAuth2UserService.class),
                mock(JwtAuthenticationFilter.class),
                mock(OAuth2AuthenticationSuccessHandler.class),
                mock(OAuth2AuthenticationFailureHandler.class),
                mock(AuthAuthenticationEntryPoint.class),
                mock(AuthAccessDeniedHandler.class),
                corsProperties(),
                new ApiDocumentationProperties()
        );

        // when
        UrlBasedCorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest(
                        HttpMethod.GET.name(),
                        "/api/v1/auth/me"
                )
        );

        // then
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedHeaders()).containsExactlyInAnyOrder(
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.AUTHORIZATION
        );
        assertThat(configuration.getAllowCredentials()).isFalse();
    }

    private CorsProperties corsProperties() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(
                List.of(
                        "https://dev.knoted.kr",
                        "http://localhost:3000"
                )
        );
        return corsProperties;
    }
}

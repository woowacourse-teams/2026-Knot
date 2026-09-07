package com.knot.backend.global.config;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.infrastructure.github.GithubOAuth2UserService;
import com.knot.backend.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.knot.backend.auth.presentation.handler.AuthAccessDeniedHandler;
import com.knot.backend.auth.presentation.handler.AuthAuthenticationEntryPoint;
import com.knot.backend.auth.presentation.handler.OAuth2AuthenticationFailureHandler;
import com.knot.backend.auth.presentation.handler.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, OAuth2LoginProperties.class, CorsProperties.class,
        ApiDocumentationProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {
    private final GithubOAuth2UserService githubOAuth2UserService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final AuthAuthenticationEntryPoint authenticationEntryPoint;
    private final AuthAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;
    private final ApiDocumentationProperties apiDocumentationProperties;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = corsProperties.getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty() || allowedOrigins.stream()
                .anyMatch(this::isInvalidCorsOrigin)) {
            throw new AuthException(AuthErrorCode.OAUTH_CONFIGURATION_INVALID);
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(
                List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.OPTIONS.name()
                )
        );
        configuration.setAllowedHeaders(
                List.of(
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.AUTHORIZATION
                )
        );
        configuration.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        // 자격증명은 Authorization 헤더로만 오간다. 쿠키를 싣지 않으므로 credentials를 열지 않는다(기획서 5.1)
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(
                "/**",
                configuration
        );
        return source;
    }

    private boolean isInvalidCorsOrigin(String origin) {
        return origin == null || origin.isBlank() || "*".equals(origin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    if (apiDocumentationProperties.isEnabled()) {
                        auth.requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/webjars/**"
                        )
                                .permitAll();
                    }
                    auth.requestMatchers(
                            "/oauth2/**",
                            "/login/**",
                            "/api/v1/auth/nickname",
                            "/api/v1/auth/logout",
                            "/actuator/health",
                            "/error"
                    )
                            .permitAll()
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/v1/invitations/*",
                                    "/api/v1/notion/oauth/callback"
                            )
                            .permitAll()
                            .anyRequest()
                            .authenticated();
                })
                .exceptionHandling(
                        exception -> exception.authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler)
                )
                // 브라우저가 자동으로 붙이는 자격증명이 없으므로 CSRF 방어 대상 자체가 사라졌다(기획서 5.1, 로드맵 Q18)
                .csrf(CsrfConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .oauth2Login(
                        oauth2 -> oauth2.userInfoEndpoint(userInfo -> userInfo.userService(githubOAuth2UserService))
                                .successHandler(successHandler)
                                .failureHandler(failureHandler)
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}

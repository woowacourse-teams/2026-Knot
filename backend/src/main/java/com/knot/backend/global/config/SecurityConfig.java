package com.knot.backend.global.config;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.infrastructure.github.GithubOAuth2UserService;
import com.knot.backend.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.knot.backend.auth.presentation.handler.AuthAuthenticationEntryPoint;
import com.knot.backend.auth.presentation.handler.JwtLogoutHandler;
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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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
    private final JwtLogoutHandler jwtLogoutHandler;
    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final ApiDocumentationProperties apiDocumentationProperties;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        String allowedOrigin = corsProperties.getAllowedOrigin();
        if (allowedOrigin == null || allowedOrigin.isBlank() || "*".equals(allowedOrigin)) {
            throw new AuthException(AuthErrorCode.OAUTH_CONFIGURATION_INVALID);
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(
                List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.OPTIONS.name()
                )
        );
        configuration.setAllowedHeaders(
                List.of(
                        HttpHeaders.CONTENT_TYPE,
                        "X-XSRF-TOKEN"
                )
        );
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(
                "/**",
                configuration
        );
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(
                cookie -> cookie.httpOnly(false)
                        .secure(jwtProperties.isSecure())
                        .sameSite("Lax")
                        .path("/")
        );

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
                            "/auth/nickname",
                            "/auth/csrf",
                            "/actuator/health",
                            "/error"
                    )
                            .permitAll()
                            .anyRequest()
                            .authenticated();
                })
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .csrf(
                        csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .oauth2Login(
                        oauth2 -> oauth2.userInfoEndpoint(userInfo -> userInfo.userService(githubOAuth2UserService))
                                .successHandler(successHandler)
                                .failureHandler(failureHandler)
                )
                .logout(logout -> logout.addLogoutHandler(jwtLogoutHandler))
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}

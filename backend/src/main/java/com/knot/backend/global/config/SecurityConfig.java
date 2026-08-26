package com.knot.backend.global.config;

import com.knot.backend.auth.infrastructure.github.GithubOAuth2UserService;
import com.knot.backend.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.knot.backend.auth.presentation.handler.AuthAuthenticationEntryPoint;
import com.knot.backend.auth.presentation.handler.JwtLogoutHandler;
import com.knot.backend.auth.presentation.handler.OAuth2AuthenticationFailureHandler;
import com.knot.backend.auth.presentation.handler.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, OAuth2LoginProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {
    private final GithubOAuth2UserService githubOAuth2UserService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final AuthAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtLogoutHandler jwtLogoutHandler;
    private final JwtProperties jwtProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository
                .withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(
                cookie -> cookie.httpOnly(false)
                        .secure(jwtProperties.isSecure())
                        .sameSite("Lax")
                        .path("/")
        );

        http.authorizeHttpRequests(
                auth -> auth.requestMatchers(
                        "/oauth2/**",
                        "/login/**",
                        "/auth/nickname",
                        "/actuator/health",
                        "/error"
                )
                        .permitAll()
                        .anyRequest()
                        .authenticated()
        )
                .exceptionHandling(
                        exception -> exception.authenticationEntryPoint(authenticationEntryPoint)
                )
                .csrf(
                        csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                                .csrfTokenRequestHandler(
                                        (
                                                request,
                                                response,
                                                csrfToken
                                        ) -> csrfToken.get()
                                )
                )
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .oauth2Login(
                        oauth2 -> oauth2
                                .userInfoEndpoint(
                                        userInfo -> userInfo.userService(githubOAuth2UserService)
                                )
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

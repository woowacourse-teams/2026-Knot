package com.knot.backend.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private JwtProvider jwtProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-jwt-secret-012345678901234567890123456789");
        properties.setExpiration(Duration.ofHours(1));
        properties.setCookieName("KNOT_ACCESS_TOKEN");
        jwtProvider = new JwtProvider(
                properties,
                Clock.systemUTC()
        );
        filter = new JwtAuthenticationFilter(
                jwtProvider,
                properties
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("JWT 쿠키가 있으면 인증 주체를 SecurityContext에 저장한다")
    void doFilter_success() throws Exception {
        // given
        AuthenticatedMember expected = AuthenticatedMember.of(
                1L,
                42L,
                "octocat",
                "https://example.com/avatar"
        );
        String token = jwtProvider.issue(expected);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        "KNOT_ACCESS_TOKEN",
                        token
                )
        );

        // when
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        // then
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).satisfies(authentication -> {
            assertThat(authentication.getPrincipal()).isEqualTo(expected);
            assertThat(authentication.getAuthorities())
                    .extracting(authority -> authority.getAuthority())
                    .containsExactly("ROLE_USER");
        });
    }

    @Test
    @DisplayName("잘못된 JWT 쿠키가 있으면 인증하지 않고 요청을 계속 처리한다")
    void doFilter_failure_invalidToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        "KNOT_ACCESS_TOKEN",
                        "invalid-token"
                )
        );

        // when
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        // then
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).isNull();
    }

    @Test
    @DisplayName("JWT 쿠키가 없으면 기존 세션 인증도 사용하지 않는다")
    void doFilter_failure_noTokenClearsExistingAuthentication() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "session",
                                null
                        )
                );
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        // then
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).isNull();
    }

    @Test
    @DisplayName("동일한 이름의 JWT 쿠키가 두 개면 인증하지 않는다")
    void doFilter_failure_duplicateTokenClearsAuthentication() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        "KNOT_ACCESS_TOKEN",
                        "first-token"
                ),
                new Cookie(
                        "KNOT_ACCESS_TOKEN",
                        "second-token"
                )
        );

        // when
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        // then
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).isNull();
    }
}

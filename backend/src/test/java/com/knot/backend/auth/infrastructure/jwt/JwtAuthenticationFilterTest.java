package com.knot.backend.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.application.DeviceAuthService;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.member.application.MemberService;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {
    private JwtProvider jwtProvider;
    private MemberService memberService;
    private DeviceAuthService deviceAuthService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-jwt-secret-012345678901234567890123456789");
        properties.setExpiration(Duration.ofHours(1));
        jwtProvider = new JwtProvider(
                properties,
                Clock.systemUTC()
        );
        memberService = mock(MemberService.class);
        when(memberService.existsById(1L)).thenReturn(true);
        deviceAuthService = mock(DeviceAuthService.class);
        filter = new JwtAuthenticationFilter(
                jwtProvider,
                memberService,
                deviceAuthService
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤더에 Bearer 토큰이 있으면 인증 주체를 SecurityContext에 저장한다")
    void doFilter_success() throws Exception {
        // given
        AuthenticatedMember expected = AuthenticatedMember.of(
                1L,
                "octocat",
                "https://example.com/avatar"
        );
        MockHttpServletRequest request = requestWithAuthorization("Bearer " + jwtProvider.issue(expected));

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
            assertThat(authentication.getAuthorities()).extracting(authority -> authority.getAuthority())
                    .containsExactly("ROLE_USER");
        });
    }

    @Test
    @DisplayName("Bearer 스킴은 대소문자를 가리지 않는다")
    void doFilter_success_caseInsensitiveScheme() throws Exception {
        // given
        AuthenticatedMember expected = AuthenticatedMember.of(
                1L,
                "octocat",
                "https://example.com/avatar"
        );
        MockHttpServletRequest request = requestWithAuthorization("bearer " + jwtProvider.issue(expected));

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
        ).isNotNull();
    }

    @Test
    @DisplayName("잘못된 Bearer 토큰이면 인증하지 않고 요청을 계속 처리한다")
    void doFilter_failure_invalidToken() throws Exception {
        // given
        MockHttpServletRequest request = requestWithAuthorization("Bearer invalid-token");

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
    @DisplayName("토큰의 회원이 더 이상 없으면 서명이 유효해도 인증하지 않는다")
    void doFilter_failure_memberNotFound() throws Exception {
        // given
        AuthenticatedMember deleted = AuthenticatedMember.of(
                2L,
                "octocat",
                null
        );
        when(memberService.existsById(2L)).thenReturn(false);
        MockHttpServletRequest request = requestWithAuthorization("Bearer " + jwtProvider.issue(deleted));

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
    @DisplayName("Authorization 헤더가 없으면 기존 세션 인증도 사용하지 않는다")
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
    @DisplayName("Bearer가 아닌 인증 스킴은 무시한다")
    void doFilter_failure_otherScheme() throws Exception {
        // given
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                "octocat",
                "https://example.com/avatar"
        );
        MockHttpServletRequest request = requestWithAuthorization("Basic " + jwtProvider.issue(member));

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
    @DisplayName("디바이스 액세스 토큰은 기기 세션이 살아 있을 때만 인증하고 회원 존재는 따로 묻지 않는다")
    void doFilter_success_deviceSessionActive() throws Exception {
        // given
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                "octocat",
                null
        );
        when(
                deviceAuthService.isSessionActive(
                        77L,
                        1L
                )
        ).thenReturn(true);
        MockHttpServletRequest request = requestWithAuthorization(
                "Bearer " + jwtProvider.issueDevice(
                        member,
                        77L
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
            AuthenticatedMember principal = (AuthenticatedMember) authentication.getPrincipal();
            assertThat(principal.isDeviceSession()).isTrue();
            assertThat(principal.getDeviceSessionId()).isEqualTo(77L);
        });
        verify(
                memberService,
                never()
        ).existsById(anyLong());
    }

    @Test
    @DisplayName("폐기된 기기 세션의 디바이스 액세스 토큰은 만료 전이라도 인증하지 않는다")
    void doFilter_failure_deviceSessionRevoked() throws Exception {
        // given
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                "octocat",
                null
        );
        when(
                deviceAuthService.isSessionActive(
                        77L,
                        1L
                )
        ).thenReturn(false);
        MockHttpServletRequest request = requestWithAuthorization(
                "Bearer " + jwtProvider.issueDevice(
                        member,
                        77L
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

    private MockHttpServletRequest requestWithAuthorization(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                HttpHeaders.AUTHORIZATION,
                headerValue
        );
        return request;
    }
}

package com.knot.backend.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    @Test
    @DisplayName("member 정보로 발급한 JWT를 다시 인증 주체로 변환한다")
    void issueAndAuthenticate_success() {
        // given
        JwtProvider provider = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                42L,
                "octocat",
                "https://example.com/avatar"
        );

        // when
        String token = provider.issue(member);
        AuthenticatedMember result = provider.authenticate(token);

        // then
        assertThat(result).isEqualTo(member);
    }

    @Test
    @DisplayName("식별자가 있는 member 정보로 JWT를 발급한다")
    void issueMember_success() {
        // given
        JwtProvider provider = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getGithubId()).thenReturn(42L);
        when(member.getNickname()).thenReturn("octocat");

        // when
        String token = provider.issue(member);

        // then
        assertThat(
                provider.authenticate(token)
                        .getMemberId()
        ).isEqualTo(1L);
    }

    @Test
    @DisplayName("만료된 JWT는 인증하지 않는다")
    void authenticate_failure_expiredToken() {
        // given
        Clock clock = mock(Clock.class);
        Instant issuedAt = Instant.parse("2026-08-24T00:00:00Z");
        when(clock.instant()).thenReturn(
                issuedAt,
                issuedAt.plusSeconds(2)
        );
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        JwtProvider provider = new JwtProvider(
                properties(Duration.ofSeconds(1)),
                clock
        );
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                42L,
                "octocat",
                null
        );
        String token = provider.issue(member);

        // when & then
        assertThatThrownBy(() -> provider.authenticate(token)).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("JWT secret이 없으면 커스텀 설정 예외를 발생시킨다")
    void create_failure_invalidJwtConfiguration() {
        JwtProperties properties = new JwtProperties();

        // when & then
        assertThatThrownBy(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("JWT expiration이 0이면 커스텀 설정 예외를 발생시킨다")
    void create_failure_zeroExpiration() {
        JwtProperties properties = properties(Duration.ZERO);

        // when & then
        assertThatThrownBy(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("JWT cookie 이름이 비어 있으면 커스텀 설정 예외를 발생시킨다")
    void create_failure_blankCookieName() {
        // given
        JwtProperties properties = properties(Duration.ofHours(1));
        properties.setCookieName(" ");

        // when & then
        assertThatThrownBy(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("Secure가 꺼진 Host 전용 cookie 이름이면 커스텀 설정 예외를 발생시킨다")
    void create_failure_insecureHostCookie() {
        // given
        JwtProperties properties = properties(Duration.ofHours(1));
        properties.setCookieName("__Host-KNOT_ACCESS_TOKEN");

        // when & then
        assertThatThrownBy(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("다른 secret으로 서명된 JWT는 인증하지 않는다")
    void authenticate_failure_wrongSecret() {
        // given
        JwtProvider issuer = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );
        JwtProperties otherProperties = properties(Duration.ofHours(1));
        otherProperties.setSecret("another-jwt-secret-012345678901234567890123456789");
        JwtProvider verifier = new JwtProvider(
                otherProperties,
                Clock.systemUTC()
        );
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                42L,
                "octocat",
                null
        );
        String token = issuer.issue(member);

        // when & then
        assertThatThrownBy(() -> verifier.authenticate(token)).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("빈 JWT는 인증하지 않는다")
    void authenticate_failure_blankToken() {
        // given
        JwtProvider provider = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );

        // when & then
        assertThatThrownBy(() -> provider.authenticate(" ")).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    private JwtProperties properties(Duration expiration) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-jwt-secret-012345678901234567890123456789");
        properties.setExpiration(expiration);
        properties.setCookieName("KNOT_ACCESS_TOKEN");
        properties.setSecure(false);
        return properties;
    }
}

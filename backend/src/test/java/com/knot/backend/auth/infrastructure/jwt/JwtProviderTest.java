package com.knot.backend.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.domain.RefreshToken;
import com.knot.backend.global.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

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
    @DisplayName("OAuth 사용자 정보로 닉네임 토큰을 발급하고 다시 복원한다")
    void issueAndAuthenticateNickname_success() {
        // given
        Instant issuedAt = Instant.parse("2026-10-01T00:00:00Z");
        JwtProvider provider = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.fixed(
                        issuedAt,
                        ZoneOffset.UTC
                )
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                "https://example.com/avatar"
        );

        // when
        String token = provider.issueNickname(oauthUser);
        OAuthUser result = provider.authenticateNickname(token);

        // then
        assertThat(result.getProvider()).isEqualTo(oauthUser.getProvider());
        assertThat(result.getExternalId()).isEqualTo(oauthUser.getExternalId());
        assertThat(result.getProfileImageUrl()).isEqualTo(oauthUser.getProfileImageUrl());
        assertThat(result.getAuthenticatedAt()).isEqualTo(issuedAt);
    }

    @Test
    @DisplayName("신규 사용자 JWT의 token_type은 ONBOARDING이다")
    void issueNickname_success_onboardingTokenType() {
        // given
        JwtProperties properties = properties(Duration.ofHours(1));
        JwtProvider provider = new JwtProvider(
                properties,
                Clock.systemUTC()
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );

        // when
        String token = provider.issueNickname(oauthUser);
        Jwt jwt = decoder(properties).decode(token);

        // then
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("ONBOARDING");
    }

    @Test
    @DisplayName("닉네임 토큰은 일반 인증 주체로 인증하지 않는다")
    void authenticate_failure_nicknameToken() {
        // given
        JwtProvider provider = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );
        String token = provider.issueNickname(oauthUser);

        // when
        Throwable thrown = catchThrowable(() -> provider.authenticate(token));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("access token은 온보딩 인증 주체로 인증하지 않는다")
    void authenticateNickname_failure_accessToken() {
        // given
        JwtProvider provider = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );
        String accessToken = provider.issue(
                AuthenticatedMember.of(
                        1L,
                        "octocat",
                        null
                )
        );

        // when
        Throwable thrown = catchThrowable(() -> provider.authenticateNickname(accessToken));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
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
                "octocat",
                null
        );
        String token = provider.issue(member);

        // when
        Throwable thrown = catchThrowable(() -> provider.authenticate(token));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("JWT secret이 없으면 커스텀 설정 예외를 발생시킨다")
    void create_failure_invalidJwtConfiguration() {
        // given
        JwtProperties properties = new JwtProperties();

        // when
        Throwable thrown = catchThrowable(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("JWT expiration이 0이면 커스텀 설정 예외를 발생시킨다")
    void create_failure_zeroExpiration() {
        // given
        JwtProperties properties = properties(Duration.ZERO);

        // when
        Throwable thrown = catchThrowable(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("닉네임 토큰 만료 시간이 0이면 커스텀 설정 예외를 발생시킨다")
    void create_failure_zeroNicknameTokenExpiration() {
        // given
        JwtProperties properties = properties(Duration.ofHours(1));
        properties.setNicknameTokenExpiration(Duration.ZERO);

        // when
        Throwable thrown = catchThrowable(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("JWT cookie 이름이 비어 있으면 커스텀 설정 예외를 발생시킨다")
    void create_failure_blankCookieName() {
        // given
        JwtProperties properties = properties(Duration.ofHours(1));
        properties.setCookieName(" ");

        // when
        Throwable thrown = catchThrowable(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("JWT issuer가 비어 있으면 커스텀 설정 예외를 발생시킨다")
    void create_failure_blankIssuer() {
        // given
        JwtProperties properties = properties(Duration.ofHours(1));
        properties.setIssuer(" ");

        // when
        Throwable thrown = catchThrowable(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("JWT audience가 비어 있으면 커스텀 설정 예외를 발생시킨다")
    void create_failure_blankAudience() {
        // given
        JwtProperties properties = properties(Duration.ofHours(1));
        properties.setAudience(" ");

        // when
        Throwable thrown = catchThrowable(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("Secure가 꺼진 Host 전용 cookie 이름이면 커스텀 설정 예외를 발생시킨다")
    void create_failure_insecureHostCookie() {
        // given
        JwtProperties properties = properties(Duration.ofHours(1));
        properties.setCookieName("__Host-KNOT_ACCESS_TOKEN");

        // when
        Throwable thrown = catchThrowable(
                () -> new JwtProvider(
                        properties,
                        Clock.systemUTC()
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.JWT_CONFIGURATION_INVALID)
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
                "octocat",
                null
        );
        String token = issuer.issue(member);

        // when
        Throwable thrown = catchThrowable(() -> verifier.authenticate(token));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("issuer와 audience가 다른 JWT는 인증하지 않는다")
    void authenticate_failure_differentTokenBoundary() {
        // given
        JwtProperties issuerProperties = properties(Duration.ofHours(1));
        issuerProperties.setIssuer("https://issuer.example");
        issuerProperties.setAudience("issuer-api");
        JwtProvider issuer = new JwtProvider(
                issuerProperties,
                Clock.systemUTC()
        );
        JwtProvider verifier = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                "octocat",
                null
        );
        String token = issuer.issue(member);

        // when
        Throwable thrown = catchThrowable(() -> verifier.authenticate(token));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
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

        // when
        Throwable thrown = catchThrowable(() -> provider.authenticate(" "));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("refresh JWT는 전용 token_type과 지정된 만료 시각을 가진다")
    void issueRefreshToken_success_refreshPurposeAndExpiration() {
        // given
        Instant issuedAt = Instant.parse("2026-10-01T00:00:00Z");
        Instant expiresAt = issuedAt.plus(Duration.ofDays(7));
        JwtProperties properties = properties(Duration.ofHours(1));
        JwtProvider provider = new JwtProvider(
                properties,
                Clock.fixed(
                        issuedAt,
                        ZoneOffset.UTC
                )
        );

        // when
        RefreshToken refreshToken = provider.issue(expiresAt);
        String token = refreshToken.getValue();
        Jwt jwt = decoder(properties).decode(token);
        provider.authenticateRefreshToken(token);

        // then
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("REFRESH");
        assertThat(jwt.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuer()).hasToString(properties.getIssuer());
        assertThat(jwt.getAudience()).contains(properties.getAudience());
        assertThat(refreshToken.getHash()).hasSize(64)
                .isNotEqualTo(token);
        assertThat(refreshToken.toString()).doesNotContain(token);
    }

    @Test
    @DisplayName("refresh 인증은 access token을 거절한다")
    void authenticateRefreshToken_failure_accessToken() {
        // given
        JwtProvider provider = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );
        String accessToken = provider.issue(
                AuthenticatedMember.of(
                        1L,
                        "octocat",
                        null
                )
        );

        // when
        Throwable thrown = catchThrowable(() -> provider.authenticateRefreshToken(accessToken));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("refresh 인증은 서명 키가 다른 토큰을 거절한다")
    void authenticateRefreshToken_failure_wrongSecret() {
        // given
        JwtProperties issuerProperties = properties(Duration.ofHours(1));
        JwtProvider issuer = new JwtProvider(
                issuerProperties,
                Clock.systemUTC()
        );
        JwtProperties verifierProperties = properties(Duration.ofHours(1));
        verifierProperties.setSecret("another-jwt-secret-012345678901234567890123456789");
        JwtProvider verifier = new JwtProvider(
                verifierProperties,
                Clock.systemUTC()
        );
        String token = issuer.issue(
                Instant.now()
                        .plus(Duration.ofDays(7))
        )
                .getValue();

        // when
        Throwable thrown = catchThrowable(() -> verifier.authenticateRefreshToken(token));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("refresh 인증은 issuer나 audience가 다른 토큰을 거절한다")
    void authenticateRefreshToken_failure_differentTokenBoundary() {
        // given
        JwtProperties issuerProperties = properties(Duration.ofHours(1));
        issuerProperties.setIssuer("https://issuer.example");
        issuerProperties.setAudience("issuer-api");
        JwtProvider issuer = new JwtProvider(
                issuerProperties,
                Clock.systemUTC()
        );
        JwtProvider verifier = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );
        String token = issuer.issue(
                Instant.now()
                        .plus(Duration.ofDays(7))
        )
                .getValue();

        // when
        Throwable thrown = catchThrowable(() -> verifier.authenticateRefreshToken(token));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("만료된 refresh JWT는 인증하지 않는다")
    void authenticateRefreshToken_failure_expiredToken() {
        // given
        Instant issuedAt = Instant.parse("2026-10-01T00:00:00Z");
        JwtProperties properties = properties(Duration.ofHours(1));
        JwtProvider issuer = new JwtProvider(
                properties,
                Clock.fixed(
                        issuedAt,
                        ZoneOffset.UTC
                )
        );
        JwtProvider verifier = new JwtProvider(
                properties,
                Clock.fixed(
                        issuedAt.plusSeconds(2),
                        ZoneOffset.UTC
                )
        );
        String token = issuer.issue(issuedAt.plusSeconds(1))
                .getValue();

        // when
        Throwable thrown = catchThrowable(() -> verifier.authenticateRefreshToken(token));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_JWT)
        );
    }

    @Test
    @DisplayName("refresh 토큰 원문으로 저장용 해시를 식별한다")
    void identifyRefreshToken_success_hashesRawValue() {
        // given
        JwtProvider provider = new JwtProvider(
                properties(Duration.ofHours(1)),
                Clock.systemUTC()
        );
        Instant expiresAt = Instant.now()
                .plus(Duration.ofDays(7));
        RefreshToken issuedToken = provider.issue(expiresAt);

        // when
        RefreshToken identifiedToken = provider.identify(issuedToken.getValue());

        // then
        assertThat(identifiedToken.getHash()).isEqualTo(issuedToken.getHash());
        assertThat(identifiedToken.getValue()).isEqualTo(issuedToken.getValue());
        assertThat(identifiedToken.toString()).doesNotContain(issuedToken.getValue());
    }

    private JwtProperties properties(Duration expiration) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-jwt-secret-012345678901234567890123456789");
        properties.setExpiration(expiration);
        properties.setCookieName("KNOT_ACCESS_TOKEN");
        properties.setSecure(false);
        return properties;
    }

    private JwtDecoder decoder(JwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(
                properties.getSecret()
                        .getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}

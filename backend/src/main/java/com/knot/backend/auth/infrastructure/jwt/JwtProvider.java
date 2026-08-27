package com.knot.backend.auth.infrastructure.jwt;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.global.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider implements AuthTokenProvider {
    private static final MacAlgorithm MAC_ALGORITHM = MacAlgorithm.HS256;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String ONBOARDING_TOKEN_TYPE = "ONBOARDING";

    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    public JwtProvider(
            JwtProperties properties,
            Clock clock
    ) {
        validateProperties(
                properties,
                clock
        );
        this.properties = properties;
        this.clock = clock;
        SecretKey secretKey = createSecretKey(properties.getSecret());
        this.encoder = NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MAC_ALGORITHM)
                .build();
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MAC_ALGORITHM)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        timestampValidator.setAllowEmptyExpiryClaim(false);
        jwtDecoder.setJwtValidator(timestampValidator);
        this.decoder = jwtDecoder;
    }

    @Override
    public String issue(AuthenticatedMember member) {
        if (member == null) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }

        Instant issuedAt = Instant.now(clock);
        JwtClaimsSet.Builder claimsBuilder = baseClaims(
                member,
                issuedAt
        );
        if (member.getProfileImageUrl() != null) {
            claimsBuilder.claim(
                    "profile_image_url",
                    member.getProfileImageUrl()
            );
        }
        return encode(claimsBuilder.build());
    }

    @Override
    public String issueNickname(OAuthUser oauthUser) {
        if (oauthUser == null) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }

        Instant issuedAt = Instant.now(clock);
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .subject(oauthUser.getExternalId())
                .issuer(properties.getIssuer())
                .audience(List.of(properties.getAudience()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.getNicknameTokenExpiration()))
                .claim(
                        "token_type",
                        ONBOARDING_TOKEN_TYPE
                )
                .claim(
                        "provider",
                        oauthUser.getProvider()
                                .name()
                );

        if (oauthUser.getProfileImageUrl() != null) {
            claimsBuilder.claim(
                    "profile_image_url",
                    oauthUser.getProfileImageUrl()
            );
        }

        return encode(claimsBuilder.build());
    }

    @Override
    public AuthenticatedMember authenticate(String token) {
        try {
            Jwt jwt = decodeAndValidate(token);
            validateTokenType(
                    jwt,
                    ACCESS_TOKEN_TYPE
            );
            return AuthenticatedMember.of(
                    positiveLong(jwt.getSubject()),
                    requiredClaim(jwt.getClaimAsString("nickname")),
                    jwt.getClaimAsString("profile_image_url")
            );
        } catch (AuthException exception) {
            if (exception.getErrorCode() == AuthErrorCode.INVALID_AUTHENTICATED_MEMBER) {
                throw new AuthException(
                        AuthErrorCode.INVALID_JWT,
                        exception
                );
            }
            throw exception;
        }
    }

    @Override
    public OAuthUser authenticateNickname(String token) {
        try {
            Jwt jwt = decodeAndValidate(token);
            validateTokenType(
                    jwt,
                    ONBOARDING_TOKEN_TYPE
            );

            OAuthProvider provider = OAuthProvider.valueOf(requiredClaim(jwt.getClaimAsString("provider")));

            return OAuthUser.of(
                    provider,
                    requiredClaim(jwt.getSubject()),
                    jwt.getClaimAsString("profile_image_url")
            );
        } catch (IllegalArgumentException exception) {
            throw new AuthException(
                    AuthErrorCode.INVALID_JWT,
                    exception
            );
        }
    }

    private SecretKey createSecretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        return new SecretKeySpec(
                secretBytes,
                HMAC_ALGORITHM
        );
    }

    private void validateProperties(
            JwtProperties properties,
            Clock clock
    ) {
        if (properties == null || clock == null) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        if (properties.getExpiration() == null || properties.getExpiration()
                .isZero() || properties.getExpiration()
                        .isNegative()) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        if (properties.getNicknameTokenExpiration() == null || properties.getNicknameTokenExpiration()
                .isZero() || properties.getNicknameTokenExpiration()
                        .isNegative()) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        if (properties.getIssuer() == null || properties.getIssuer()
                .isBlank() || properties.getAudience() == null || properties.getAudience()
                        .isBlank()) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        validateCookieName(
                properties.getCookieName(),
                properties.isSecure()
        );
        validateCookieName(
                properties.getNicknameCookieName(),
                properties.isSecure()
        );
    }

    private void validateCookieName(
            String cookieName,
            boolean secure
    ) {
        if (cookieName == null || cookieName.isBlank()) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        if (cookieName.startsWith("__Host-") && !secure) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
    }

    private Jwt decodeAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_JWT);
        }

        try {
            Jwt jwt = decoder.decode(token);
            validateTokenClaims(jwt);
            return jwt;
        } catch (AuthException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthException(
                    AuthErrorCode.INVALID_JWT,
                    exception
            );
        }
    }

    private long positiveLong(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_JWT);
        }

        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new AuthException(AuthErrorCode.INVALID_JWT);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new AuthException(
                    AuthErrorCode.INVALID_JWT,
                    exception
            );
        }
    }

    private String requiredClaim(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_JWT);
        }
        return value;
    }

    private JwtClaimsSet.Builder baseClaims(
            AuthenticatedMember member,
            Instant issuedAt
    ) {
        return JwtClaimsSet.builder()
                .subject(String.valueOf(member.getMemberId()))
                .issuer(properties.getIssuer())
                .audience(List.of(properties.getAudience()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.getExpiration()))
                .claim(
                        "token_type",
                        ACCESS_TOKEN_TYPE
                )
                .claim(
                        "nickname",
                        member.getNickname()
                );
    }

    private void validateTokenType(
            Jwt jwt,
            String expectedType
    ) {
        if (!expectedType.equals(jwt.getClaimAsString("token_type"))) {
            throw new AuthException(AuthErrorCode.INVALID_JWT);
        }
    }

    private void validateTokenClaims(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        if (!properties.getIssuer()
                .equals(jwt.getClaimAsString("iss")) || audience == null
                || !audience.contains(properties.getAudience())) {
            throw new AuthException(AuthErrorCode.INVALID_JWT);
        }
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MAC_ALGORITHM)
                                .build(),
                        claims
                )
        )
                .getTokenValue();
    }
}

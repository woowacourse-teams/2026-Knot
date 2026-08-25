package com.knot.backend.auth.infrastructure.jwt;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.member.domain.Member;
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
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider implements AuthTokenProvider {
    private static final MacAlgorithm MAC_ALGORITHM = MacAlgorithm.HS256;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String ISSUER = "https://knot.local";
    private static final String AUDIENCE = "knot-api";
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
    public String issue(Member member) {
        if (member == null || member.getId() == null) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }

        return issue(
                AuthenticatedMember.of(
                        member.getId(),
                        member.getGithubId(),
                        member.getNickname(),
                        member.getProfileImageUrl()
                )
        );
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
    public AuthenticatedMember authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_JWT);
        }

        try {
            Jwt jwt = decoder.decode(token);
            validateTokenClaims(jwt);
            return AuthenticatedMember.of(
                    positiveLong(jwt.getSubject()),
                    positiveLong(jwt.getClaimAsString("github_id")),
                    requiredClaim(jwt.getClaimAsString("nickname")),
                    jwt.getClaimAsString("profile_image_url")
            );
        } catch (JwtException exception) {
            throw new AuthException(
                    AuthErrorCode.INVALID_JWT,
                    exception
            );
        } catch (AuthException exception) {
            if (exception.getErrorCode() == AuthErrorCode.INVALID_AUTHENTICATED_MEMBER) {
                throw new AuthException(
                        AuthErrorCode.INVALID_JWT,
                        exception
                );
            }
            throw exception;
        } catch (RuntimeException exception) {
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
        if (properties.getCookieName() == null || properties.getCookieName()
                .isBlank()) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
        if (properties.getCookieName()
                .startsWith("__Host-") && !properties.isSecure()) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
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
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.getExpiration()))
                .claim(
                        "github_id",
                        String.valueOf(member.getGithubId())
                )
                .claim(
                        "nickname",
                        member.getNickname()
                );
    }

    private void validateTokenClaims(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        if (!ISSUER.equals(jwt.getClaimAsString("iss")) || audience == null
                || !audience.contains(AUDIENCE)) {
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

package com.knot.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    private static final Duration IDLE_LIFETIME = Duration.ofDays(7);
    private static final Duration ABSOLUTE_LIFETIME = Duration.ofDays(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "refresh_token_hash", nullable = false, length = 64, unique = true)
    private String refreshTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSession() {}

    private AuthSession(
            long memberId,
            String refreshTokenHash,
            Instant createdAt,
            Instant initialOAuthLoginAt
    ) {
        if (memberId <= 0 || refreshTokenHash == null || !refreshTokenHash.matches("[0-9a-f]{64}") || createdAt == null
                || initialOAuthLoginAt == null || initialOAuthLoginAt.isAfter(createdAt)) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_SESSION);
        }
        Instant absoluteExpiresAt = initialOAuthLoginAt.plus(ABSOLUTE_LIFETIME);
        if (!absoluteExpiresAt.isAfter(createdAt)) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_SESSION);
        }
        this.memberId = memberId;
        this.refreshTokenHash = refreshTokenHash;
        this.createdAt = createdAt;
        Instant idleExpiresAt = createdAt.plus(IDLE_LIFETIME);
        this.expiresAt = idleExpiresAt.isBefore(absoluteExpiresAt) ? idleExpiresAt : absoluteExpiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    public static AuthSession create(
            long memberId,
            String refreshTokenHash,
            Instant createdAt
    ) {
        return create(
                memberId,
                refreshTokenHash,
                createdAt,
                createdAt
        );
    }

    public static AuthSession create(
            long memberId,
            String refreshTokenHash,
            Instant createdAt,
            Instant initialOAuthLoginAt
    ) {
        return new AuthSession(
                memberId,
                refreshTokenHash,
                createdAt,
                initialOAuthLoginAt
        );
    }

    public static Instant initialRefreshExpirationAt(
            Instant sessionCreatedAt,
            Instant initialOAuthLoginAt
    ) {
        if (sessionCreatedAt == null || initialOAuthLoginAt == null || initialOAuthLoginAt.isAfter(sessionCreatedAt)) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_SESSION);
        }
        Instant absoluteExpiresAt = initialOAuthLoginAt.plus(ABSOLUTE_LIFETIME);
        if (!absoluteExpiresAt.isAfter(sessionCreatedAt)) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_SESSION);
        }
        Instant idleExpiresAt = sessionCreatedAt.plus(IDLE_LIFETIME);
        return idleExpiresAt.isBefore(absoluteExpiresAt) ? idleExpiresAt : absoluteExpiresAt;
    }

    public boolean isActive(Instant now) {
        return now != null && !now.isBefore(createdAt) && revokedAt == null && now.isBefore(expiresAt)
                && now.isBefore(absoluteExpiresAt);
    }

    public boolean revoke(Instant revokedAt) {
        if (revokedAt == null || revokedAt.isBefore(createdAt)) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_SESSION);
        }
        if (!isActive(revokedAt)) {
            return false;
        }
        this.revokedAt = revokedAt;
        return true;
    }

    public Duration remainingRefreshLifetime(Instant now) {
        if (!isActive(now)) {
            return Duration.ZERO;
        }
        Instant refreshExpiresAt = expiresAt.isBefore(absoluteExpiresAt) ? expiresAt : absoluteExpiresAt;
        return Duration.between(
                now,
                refreshExpiresAt
        );
    }

    public Instant nextRefreshExpirationAt(Instant now) {
        if (!isActive(now)) {
            throw new AuthException(AuthErrorCode.UNAUTHENTICATED);
        }
        Instant idleExpiresAt = now.plus(IDLE_LIFETIME);
        return idleExpiresAt.isBefore(absoluteExpiresAt) ? idleExpiresAt : absoluteExpiresAt;
    }

    public String rotateRefreshToken(
            String newRefreshTokenHash,
            Instant now
    ) {
        if (newRefreshTokenHash == null || !newRefreshTokenHash.matches("[0-9a-f]{64}")) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_SESSION);
        }
        Instant nextExpiresAt = nextRefreshExpirationAt(now);
        if (newRefreshTokenHash.equals(refreshTokenHash)) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_SESSION);
        }
        String previousRefreshTokenHash = refreshTokenHash;
        refreshTokenHash = newRefreshTokenHash;
        expiresAt = nextExpiresAt;
        return previousRefreshTokenHash;
    }

}

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

    private AuthSession(long memberId, String refreshTokenHash, Instant createdAt) {
        if (memberId <= 0 || refreshTokenHash == null || !refreshTokenHash.matches("[0-9a-f]{64}")
                || createdAt == null) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_SESSION);
        }
        this.memberId = memberId;
        this.refreshTokenHash = refreshTokenHash;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plus(IDLE_LIFETIME);
        this.absoluteExpiresAt = createdAt.plus(ABSOLUTE_LIFETIME);
    }

    public static AuthSession create(long memberId, String refreshTokenHash, Instant createdAt) {
        return new AuthSession(memberId, refreshTokenHash, createdAt);
    }

    public boolean isActive(Instant now) {
        return now != null && !now.isBefore(createdAt) && revokedAt == null
                && now.isBefore(expiresAt) && now.isBefore(absoluteExpiresAt);
    }
}

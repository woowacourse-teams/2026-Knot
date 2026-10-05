package com.knot.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "auth_session_consumed_refresh_tokens")
public class ConsumedRefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "auth_session_id", nullable = false)
    private Long authSessionId;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected ConsumedRefreshToken() {}

    private ConsumedRefreshToken(
            long authSessionId,
            String refreshTokenHash,
            Instant consumedAt
    ) {
        if (authSessionId <= 0 || refreshTokenHash == null || !refreshTokenHash.matches("[0-9a-f]{64}")
                || consumedAt == null) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_SESSION);
        }
        this.refreshTokenHash = refreshTokenHash;
        this.authSessionId = authSessionId;
        this.consumedAt = consumedAt;
    }

    public static ConsumedRefreshToken create(
            long authSessionId,
            String refreshTokenHash,
            Instant consumedAt
    ) {
        return new ConsumedRefreshToken(
                authSessionId,
                refreshTokenHash,
                consumedAt
        );
    }
}

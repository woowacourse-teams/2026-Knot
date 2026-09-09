package com.knot.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

/**
 * 세션의 리프레시 토큰 한 세대(기획서 5.2 토큰 규격, RFC 9700 §4.14.2 rotation).
 *
 * 갱신할 때마다 새 행을 만들고 이전 행은 `rotatedAt`을 채운다. 이미 rotation된 토큰이 다시 오면 탈취로 보고 세션 전체를
 * 폐기한다. DB에는 SHA-256 해시만 있고 원문은 발급 응답에만 실린다.
 */
@Getter
@Entity
@Table(name = "device_refresh_tokens")
public class DeviceRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = OpaqueToken.HASH_LENGTH)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    protected DeviceRefreshToken() {}

    private DeviceRefreshToken(
            long sessionId,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (sessionId <= 0 || tokenHash == null || tokenHash.length() != OpaqueToken.HASH_LENGTH) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.createdAt = truncate(createdAt);
        this.expiresAt = truncate(expiresAt);
    }

    public static DeviceRefreshToken issue(
            long sessionId,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new DeviceRefreshToken(
                sessionId,
                tokenHash,
                createdAt,
                expiresAt
        );
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }

    public boolean isExpiredAt(Instant pointInTime) {
        if (pointInTime == null) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        return !pointInTime.isBefore(expiresAt);
    }

    public void rotate(Instant rotatedAt) {
        if (rotatedAt == null || rotatedAt.isBefore(createdAt)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (isRotated()) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        this.rotatedAt = truncate(rotatedAt);
    }

    private static Instant truncate(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }
}

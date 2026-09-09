package com.knot.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

/**
 * 기기 로그인 1건(기획서 5.2). id가 액세스 토큰 `sid` 클레임이자 기기 목록·폐기 API의 공개 id다.
 *
 * 리프레시 토큰은 별도 행(`DeviceRefreshToken`)으로 rotation하므로 세션 id는 로그인부터 폐기까지 바뀌지 않는다.
 * `lastUsedAt`은 요청마다 쓰지 않고 토큰을 발급·갱신할 때만 옮긴다(로드맵 Q58).
 */
@Getter
@Entity
@Table(name = "device_sessions")
public class DeviceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "device_name", nullable = false, updatable = false, length = DeviceInfo.MAX_NAME_LENGTH)
    private String deviceName;

    @Column(name = "platform", nullable = false, updatable = false, length = DeviceInfo.MAX_PLATFORM_LENGTH)
    private String platform;

    @Column(name = "app_version", updatable = false, length = DeviceInfo.MAX_APP_VERSION_LENGTH)
    private String appVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected DeviceSession() {}

    private DeviceSession(
            long memberId,
            DeviceInfo device,
            Instant createdAt
    ) {
        if (memberId <= 0) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        if (device == null || createdAt == null) {
            throw new AuthException(AuthErrorCode.INVALID_DEVICE_INFO);
        }
        this.memberId = memberId;
        this.deviceName = device.name();
        this.platform = device.platform();
        this.appVersion = device.appVersion();
        this.createdAt = truncate(createdAt);
        this.lastUsedAt = this.createdAt;
    }

    public static DeviceSession create(
            long memberId,
            DeviceInfo device,
            Instant createdAt
    ) {
        return new DeviceSession(
                memberId,
                device,
                createdAt
        );
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void touch(Instant usedAt) {
        if (usedAt == null || usedAt.isBefore(createdAt)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (!isActive()) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        this.lastUsedAt = truncate(usedAt);
    }

    /** 이미 폐기된 세션을 다시 폐기해도 실패하지 않는다(RFC 7009 — revoke는 멱등) */
    public void revoke(Instant revokedAt) {
        if (revokedAt == null || revokedAt.isBefore(createdAt)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (this.revokedAt == null) {
            this.revokedAt = truncate(revokedAt);
        }
    }

    private static Instant truncate(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }
}

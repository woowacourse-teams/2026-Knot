package com.knot.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

/**
 * 시스템 브라우저 로그인 결과를 앱으로 넘기는 일회용 코드(기획서 5.2 `device_code`).
 *
 * TTL 120초, 1회용, `code_challenge`에 묶인다. 코드만 가로채도 verifier 없이는 교환할 수 없고(5.3), 이미 쓴 코드가 다시 오면
 * 그 코드로 만든 세션을 폐기한다. 기존 회원이면 `memberId`, 닉네임을 아직 정하지 않은 사용자면 OAuth 식별 정보를 담아
 * 교환 시 온보딩 토큰을 발급한다(로드맵 Q57).
 */
@Getter
@Entity
@Table(name = "device_authorization_codes")
public class DeviceAuthorizationCode {
    private static final int MAX_CODE_CHALLENGE_LENGTH = 128;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_hash", nullable = false, updatable = false, length = OpaqueToken.HASH_LENGTH)
    private String codeHash;

    @Column(name = "code_challenge", nullable = false, updatable = false, length = MAX_CODE_CHALLENGE_LENGTH)
    private String codeChallenge;

    @Column(name = "member_id", updatable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", updatable = false, length = 20)
    private OAuthProvider oauthProvider;

    @Column(name = "oauth_provider_user_id", updatable = false, length = 255)
    private String oauthProviderUserId;

    @Column(name = "oauth_profile_image_url", updatable = false, length = 500)
    private String oauthProfileImageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumed_session_id")
    private Long consumedSessionId;

    protected DeviceAuthorizationCode() {}

    private DeviceAuthorizationCode(
            String codeHash,
            String codeChallenge,
            Long memberId,
            OAuthUser oauthUser,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (codeHash == null || codeHash.length() != OpaqueToken.HASH_LENGTH) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (codeChallenge == null || codeChallenge.isBlank() || codeChallenge.length() > MAX_CODE_CHALLENGE_LENGTH) {
            throw new AuthException(AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID);
        }
        if (createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if ((memberId == null) == (oauthUser == null)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (memberId != null && memberId <= 0) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        this.codeHash = codeHash;
        this.codeChallenge = codeChallenge;
        this.memberId = memberId;
        if (oauthUser != null) {
            this.oauthProvider = oauthUser.getProvider();
            this.oauthProviderUserId = oauthUser.getExternalId();
            this.oauthProfileImageUrl = oauthUser.getProfileImageUrl();
        }
        this.createdAt = truncate(createdAt);
        this.expiresAt = truncate(expiresAt);
    }

    public static DeviceAuthorizationCode forMember(
            String codeHash,
            String codeChallenge,
            long memberId,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new DeviceAuthorizationCode(
                codeHash,
                codeChallenge,
                memberId,
                null,
                createdAt,
                expiresAt
        );
    }

    public static DeviceAuthorizationCode forOAuthUser(
            String codeHash,
            String codeChallenge,
            OAuthUser oauthUser,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (oauthUser == null) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }
        return new DeviceAuthorizationCode(
                codeHash,
                codeChallenge,
                null,
                oauthUser,
                createdAt,
                expiresAt
        );
    }

    public boolean requiresNickname() {
        return memberId == null;
    }

    public OAuthUser toOAuthUser() {
        if (!requiresNickname()) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        return OAuthUser.of(
                oauthProvider,
                oauthProviderUserId,
                oauthProfileImageUrl
        );
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpiredAt(Instant pointInTime) {
        if (pointInTime == null) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        return !pointInTime.isBefore(expiresAt);
    }

    public boolean matchesVerifier(String codeVerifier) {
        return OpaqueToken.matchesCodeChallenge(
                codeVerifier,
                codeChallenge
        );
    }

    /**
     * @param sessionId
     *            이 코드로 만든 세션. 온보딩(닉네임 필요) 경로는 세션이 없으므로 null
     */
    public void consume(
            Instant consumedAt,
            Long sessionId
    ) {
        if (consumedAt == null || consumedAt.isBefore(createdAt)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (isConsumed() || isExpiredAt(consumedAt)) {
            throw new AuthException(AuthErrorCode.DEVICE_CODE_INVALID);
        }
        this.consumedAt = truncate(consumedAt);
        this.consumedSessionId = sessionId;
    }

    private static Instant truncate(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }
}

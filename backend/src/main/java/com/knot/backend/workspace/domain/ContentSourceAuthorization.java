package com.knot.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

@Getter
@Entity
@Table(name = "content_source_authorizations")
public class ContentSourceAuthorization {
    public static final int MAX_STATE_HASH_LENGTH = 255;
    public static final int MAX_CALLBACK_URI_LENGTH = 2_048;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private ContentSourceProvider provider;

    @Column(name = "authorizing_member_id", nullable = false, updatable = false)
    private Long authorizingMemberId;

    @Column(name = "state_hash", nullable = false, updatable = false, length = MAX_STATE_HASH_LENGTH)
    private String stateHash;

    @Column(name = "callback_uri", nullable = false, updatable = false, length = MAX_CALLBACK_URI_LENGTH)
    private String callbackUriValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ContentSourceAuthorization() {}

    private ContentSourceAuthorization(
            Long workspaceId,
            ContentSourceProvider provider,
            Long authorizingMemberId,
            String stateHash,
            URI callbackUri,
            Instant createdAt,
            Instant expiresAt
    ) {
        validateWorkspaceId(workspaceId);
        validateProvider(provider);
        validateAuthorizingMemberId(authorizingMemberId);
        validateStateHash(stateHash);
        validateCallbackUri(callbackUri);
        validateCreatedAt(createdAt);
        validateExpiresAt(
                createdAt,
                expiresAt
        );
        this.workspaceId = workspaceId;
        this.provider = provider;
        this.authorizingMemberId = authorizingMemberId;
        this.stateHash = stateHash;
        this.callbackUriValue = callbackUri.toString();
        this.createdAt = truncateToDatabasePrecision(createdAt);
        this.expiresAt = truncateToDatabasePrecision(expiresAt);
    }

    public static ContentSourceAuthorization create(
            Long workspaceId,
            ContentSourceProvider provider,
            Long authorizingMemberId,
            String stateHash,
            URI callbackUri,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new ContentSourceAuthorization(
                workspaceId,
                provider,
                authorizingMemberId,
                stateHash,
                callbackUri,
                createdAt,
                expiresAt
        );
    }

    public void invalidate(Instant invalidatedAt) {
        validateInvalidatedAt(invalidatedAt);
        if (this.invalidatedAt == null) {
            this.invalidatedAt = truncateToDatabasePrecision(invalidatedAt);
        }
    }

    public void consume(Instant consumedAt) {
        validatePointInTime(consumedAt);
        if (isExpiredAt(consumedAt)) {
            throw new ContentSourceException(ContentSourceErrorCode.EXPIRED_CONTENT_SOURCE_AUTHORIZATION);
        }
        if (consumedAt.isBefore(createdAt) || invalidatedAt != null || this.consumedAt != null) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
        this.consumedAt = truncateToDatabasePrecision(consumedAt);
    }

    public boolean isUsableAt(Instant pointInTime) {
        validatePointInTime(pointInTime);
        return !pointInTime.isBefore(createdAt) && !isExpiredAt(pointInTime) && consumedAt == null
                && invalidatedAt == null;
    }

    private boolean isExpiredAt(Instant pointInTime) {
        return !pointInTime.isBefore(expiresAt);
    }

    public URI getCallbackUri() {
        return URI.create(callbackUriValue);
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private void validateProvider(ContentSourceProvider provider) {
        if (provider == null) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private void validateAuthorizingMemberId(Long authorizingMemberId) {
        if (authorizingMemberId == null || authorizingMemberId <= 0) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private void validateStateHash(String stateHash) {
        if (stateHash == null || stateHash.isBlank() || stateHash.length() > MAX_STATE_HASH_LENGTH) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private void validateCallbackUri(URI callbackUri) {
        if (callbackUri == null || callbackUri.toString()
                .isBlank()
                || callbackUri.toString()
                        .length() > MAX_CALLBACK_URI_LENGTH) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private void validateExpiresAt(
            Instant createdAt,
            Instant expiresAt
    ) {
        if (expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private void validatePointInTime(Instant pointInTime) {
        if (pointInTime == null) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private void validateInvalidatedAt(Instant invalidatedAt) {
        if (invalidatedAt == null || invalidatedAt.isBefore(createdAt)) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private Instant truncateToDatabasePrecision(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}

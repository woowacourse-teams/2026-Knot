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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

@Getter
@Entity
@Table(name = "content_source_connections")
public class ContentSourceConnection {
    public static final int MAX_SECRET_ENVELOPE_LENGTH = 1_024;
    public static final int MAX_EXTERNAL_ID_LENGTH = 255;
    public static final int MAX_EXTERNAL_SOURCE_NAME_LENGTH = 255;
    public static final int MAX_EXTERNAL_SOURCE_ICON_LENGTH = 2_048;
    public static final int MAX_PROVIDER_REQUEST_ID_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private ContentSourceProvider provider;

    @Column(name = "access_credential_ciphertext", nullable = false, length = MAX_SECRET_ENVELOPE_LENGTH)
    private String accessCredentialCiphertext;

    @Column(name = "refresh_credential_ciphertext", length = MAX_SECRET_ENVELOPE_LENGTH)
    private String refreshCredentialCiphertext;

    @Column(name = "external_source_id", nullable = false, length = MAX_EXTERNAL_ID_LENGTH)
    private String externalSourceId;

    @Column(name = "external_source_name", length = MAX_EXTERNAL_SOURCE_NAME_LENGTH)
    private String externalSourceName;

    @Column(name = "external_source_icon", length = MAX_EXTERNAL_SOURCE_ICON_LENGTH)
    private String externalSourceIcon;

    @Column(name = "provider_connection_id", nullable = false, length = MAX_EXTERNAL_ID_LENGTH)
    private String providerConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_owner_type", nullable = false, length = 32)
    private ContentSourceAuthorizationOwnerType authorizationOwnerType;

    @Column(name = "authorization_owner_id", length = MAX_EXTERNAL_ID_LENGTH)
    private String authorizationOwnerId;

    @Column(name = "external_template_id", length = MAX_EXTERNAL_ID_LENGTH)
    private String externalTemplateId;

    @Column(name = "provider_request_id", length = MAX_PROVIDER_REQUEST_ID_LENGTH)
    private String providerRequestId;

    @Column(name = "authorizing_member_id", nullable = false)
    private Long authorizingMemberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ContentSourceConnection() {}

    private ContentSourceConnection(
            Long workspaceId,
            ContentSourceProvider provider,
            String accessCredentialCiphertext,
            String refreshCredentialCiphertext,
            String externalSourceId,
            String externalSourceName,
            String externalSourceIcon,
            String providerConnectionId,
            ContentSourceAuthorizationOwnerType authorizationOwnerType,
            String authorizationOwnerId,
            String externalTemplateId,
            String providerRequestId,
            Long authorizingMemberId,
            Instant createdAt
    ) {
        validateWorkspaceId(workspaceId);
        validateProvider(provider);
        validateConnectionValues(
                accessCredentialCiphertext,
                refreshCredentialCiphertext,
                externalSourceId,
                externalSourceName,
                externalSourceIcon,
                providerConnectionId,
                authorizationOwnerType,
                authorizationOwnerId,
                externalTemplateId,
                providerRequestId,
                authorizingMemberId
        );
        validateUpdatedAt(createdAt);
        this.workspaceId = workspaceId;
        this.provider = provider;
        updateConnectionValues(
                accessCredentialCiphertext,
                refreshCredentialCiphertext,
                externalSourceId,
                externalSourceName,
                externalSourceIcon,
                providerConnectionId,
                authorizationOwnerType,
                authorizationOwnerId,
                externalTemplateId,
                providerRequestId,
                authorizingMemberId
        );
        this.createdAt = truncateToDatabasePrecision(createdAt);
        this.updatedAt = truncateToDatabasePrecision(createdAt);
    }

    public static ContentSourceConnection create(
            Long workspaceId,
            ContentSourceProvider provider,
            String accessCredentialCiphertext,
            String refreshCredentialCiphertext,
            String externalSourceId,
            String externalSourceName,
            String externalSourceIcon,
            String providerConnectionId,
            ContentSourceAuthorizationOwnerType authorizationOwnerType,
            String authorizationOwnerId,
            String externalTemplateId,
            String providerRequestId,
            Long authorizingMemberId,
            Instant createdAt
    ) {
        return new ContentSourceConnection(
                workspaceId,
                provider,
                accessCredentialCiphertext,
                refreshCredentialCiphertext,
                externalSourceId,
                externalSourceName,
                externalSourceIcon,
                providerConnectionId,
                authorizationOwnerType,
                authorizationOwnerId,
                externalTemplateId,
                providerRequestId,
                authorizingMemberId,
                createdAt
        );
    }

    public void replace(
            ContentSourceProvider provider,
            String accessCredentialCiphertext,
            String refreshCredentialCiphertext,
            String externalSourceId,
            String externalSourceName,
            String externalSourceIcon,
            String providerConnectionId,
            ContentSourceAuthorizationOwnerType authorizationOwnerType,
            String authorizationOwnerId,
            String externalTemplateId,
            String providerRequestId,
            Long authorizingMemberId,
            Instant updatedAt
    ) {
        validateMatchingProvider(provider);
        validateConnectionValues(
                accessCredentialCiphertext,
                refreshCredentialCiphertext,
                externalSourceId,
                externalSourceName,
                externalSourceIcon,
                providerConnectionId,
                authorizationOwnerType,
                authorizationOwnerId,
                externalTemplateId,
                providerRequestId,
                authorizingMemberId
        );
        validateUpdatedAt(updatedAt);
        updateConnectionValues(
                accessCredentialCiphertext,
                refreshCredentialCiphertext,
                externalSourceId,
                externalSourceName,
                externalSourceIcon,
                providerConnectionId,
                authorizationOwnerType,
                authorizationOwnerId,
                externalTemplateId,
                providerRequestId,
                authorizingMemberId
        );
        this.updatedAt = truncateToDatabasePrecision(updatedAt);
    }

    private void updateConnectionValues(
            String accessCredentialCiphertext,
            String refreshCredentialCiphertext,
            String externalSourceId,
            String externalSourceName,
            String externalSourceIcon,
            String providerConnectionId,
            ContentSourceAuthorizationOwnerType authorizationOwnerType,
            String authorizationOwnerId,
            String externalTemplateId,
            String providerRequestId,
            Long authorizingMemberId
    ) {
        this.accessCredentialCiphertext = accessCredentialCiphertext;
        this.refreshCredentialCiphertext = refreshCredentialCiphertext;
        this.externalSourceId = externalSourceId;
        this.externalSourceName = externalSourceName;
        this.externalSourceIcon = externalSourceIcon;
        this.providerConnectionId = providerConnectionId;
        this.authorizationOwnerType = authorizationOwnerType;
        this.authorizationOwnerId = authorizationOwnerId;
        this.externalTemplateId = externalTemplateId;
        this.providerRequestId = providerRequestId;
        this.authorizingMemberId = authorizingMemberId;
    }

    private void validateConnectionValues(
            String accessCredentialCiphertext,
            String refreshCredentialCiphertext,
            String externalSourceId,
            String externalSourceName,
            String externalSourceIcon,
            String providerConnectionId,
            ContentSourceAuthorizationOwnerType authorizationOwnerType,
            String authorizationOwnerId,
            String externalTemplateId,
            String providerRequestId,
            Long authorizingMemberId
    ) {
        validateRequiredText(
                accessCredentialCiphertext,
                MAX_SECRET_ENVELOPE_LENGTH
        );
        validateOptionalText(
                refreshCredentialCiphertext,
                MAX_SECRET_ENVELOPE_LENGTH
        );
        validateRequiredText(
                externalSourceId,
                MAX_EXTERNAL_ID_LENGTH
        );
        validateOptionalText(
                externalSourceName,
                MAX_EXTERNAL_SOURCE_NAME_LENGTH
        );
        validateOptionalText(
                externalSourceIcon,
                MAX_EXTERNAL_SOURCE_ICON_LENGTH
        );
        validateRequiredText(
                providerConnectionId,
                MAX_EXTERNAL_ID_LENGTH
        );
        validateAuthorizationOwner(
                authorizationOwnerType,
                authorizationOwnerId
        );
        validateOptionalText(
                externalTemplateId,
                MAX_EXTERNAL_ID_LENGTH
        );
        validateOptionalText(
                providerRequestId,
                MAX_PROVIDER_REQUEST_ID_LENGTH
        );
        validateAuthorizingMemberId(authorizingMemberId);
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw invalidConnection();
        }
    }

    private void validateProvider(ContentSourceProvider provider) {
        if (provider == null) {
            throw invalidConnection();
        }
    }

    private void validateMatchingProvider(ContentSourceProvider provider) {
        validateProvider(provider);
        if (this.provider != provider) {
            throw new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_PROVIDER_MISMATCH);
        }
    }

    private void validateRequiredText(
            String value,
            int maxLength
    ) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalidConnection();
        }
    }

    private void validateOptionalText(
            String value,
            int maxLength
    ) {
        if (value != null && (value.isBlank() || value.length() > maxLength)) {
            throw invalidConnection();
        }
    }

    private void validateAuthorizingMemberId(Long authorizingMemberId) {
        if (authorizingMemberId == null || authorizingMemberId <= 0) {
            throw invalidConnection();
        }
    }

    private void validateAuthorizationOwner(
            ContentSourceAuthorizationOwnerType authorizationOwnerType,
            String authorizationOwnerId
    ) {
        validateOptionalText(
                authorizationOwnerId,
                MAX_EXTERNAL_ID_LENGTH
        );
        boolean validUserOwner = authorizationOwnerType == ContentSourceAuthorizationOwnerType.USER
                && authorizationOwnerId != null;
        boolean validWorkspaceOwner = authorizationOwnerType == ContentSourceAuthorizationOwnerType.WORKSPACE
                && authorizationOwnerId == null;
        if (!validUserOwner && !validWorkspaceOwner) {
            throw invalidConnection();
        }
    }

    private void validateUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw invalidConnection();
        }
    }

    private ContentSourceException invalidConnection() {
        return new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_CONNECTION);
    }

    private Instant truncateToDatabasePrecision(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}

package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ContentSourceAuthorizationContext;
import com.knot.backend.workspace.application.dto.result.AuthorizedContentSource;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class ContentSourceConnectionService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ContentSourceAuthorizationRepository authorizationRepository;
    private final ContentSourceConnectionRepository connectionRepository;
    private final ContentSourceSecretProtector secretProtector;
    private final Clock clock;

    @Transactional
    public void connect(
            ContentSourceAuthorizationContext authorization,
            AuthorizedContentSource authorizedContentSource
    ) {
        workspaceRepository.findByIdForUpdate(authorization.workspaceId())
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateLatestAuthorization(authorization);
        validateOwner(authorization);
        validateProvider(
                authorization,
                authorizedContentSource
        );

        Instant now = currentTime();
        String accessCredentialCiphertext = secretProtector.encrypt(
                authorization.workspaceId(),
                authorization.provider(),
                ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                authorizedContentSource.accessCredential()
        );
        String refreshCredentialCiphertext = encryptRefreshCredential(
                authorization.workspaceId(),
                authorization.provider(),
                authorizedContentSource.refreshCredential()
        );
        ContentSourceConnection connection = connectionRepository
                .findByWorkspaceIdAndProviderForUpdate(
                        authorization.workspaceId(),
                        authorization.provider()
                )
                .map(
                        existing -> replace(
                                existing,
                                authorization,
                                authorizedContentSource,
                                accessCredentialCiphertext,
                                refreshCredentialCiphertext,
                                now
                        )
                )
                .orElseGet(
                        () -> create(
                                authorization,
                                authorizedContentSource,
                                accessCredentialCiphertext,
                                refreshCredentialCiphertext,
                                now
                        )
                );
        connectionRepository.save(connection);
    }

    private void validateLatestAuthorization(ContentSourceAuthorizationContext authorization) {
        if (authorizationRepository.existsNewerAuthorization(
                authorization.workspaceId(),
                authorization.provider(),
                authorization.authorizationId()
        )) {
            throw new ContentSourceException(ContentSourceErrorCode.STALE_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private void validateOwner(ContentSourceAuthorizationContext authorization) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                authorization.workspaceId(),
                authorization.authorizingMemberId(),
                WorkspaceMemberRole.OWNER
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        }
    }

    private void validateProvider(
            ContentSourceAuthorizationContext authorization,
            AuthorizedContentSource authorizedContentSource
    ) {
        if (authorization.provider() == null || authorizedContentSource.provider() == null
                || authorization.provider() != authorizedContentSource.provider()) {
            throw new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_PROVIDER_MISMATCH);
        }
    }

    private String encryptRefreshCredential(
            Long workspaceId,
            ContentSourceProvider provider,
            String refreshCredential
    ) {
        if (refreshCredential == null) {
            return null;
        }
        return secretProtector.encrypt(
                workspaceId,
                provider,
                ContentSourceCredentialKind.REFRESH_CREDENTIAL,
                refreshCredential
        );
    }

    private ContentSourceConnection replace(
            ContentSourceConnection connection,
            ContentSourceAuthorizationContext authorization,
            AuthorizedContentSource authorizedContentSource,
            String accessCredentialCiphertext,
            String refreshCredentialCiphertext,
            Instant now
    ) {
        connection.replace(
                authorization.provider(),
                accessCredentialCiphertext,
                refreshCredentialCiphertext,
                authorizedContentSource.externalSourceId(),
                authorizedContentSource.externalSourceName(),
                authorizedContentSource.externalSourceIcon(),
                authorizedContentSource.providerConnectionId(),
                authorizedContentSource.authorizationOwnerType(),
                authorizedContentSource.authorizationOwnerId(),
                authorizedContentSource.externalTemplateId(),
                authorizedContentSource.providerRequestId(),
                authorization.authorizingMemberId(),
                now
        );
        return connection;
    }

    private ContentSourceConnection create(
            ContentSourceAuthorizationContext authorization,
            AuthorizedContentSource authorizedContentSource,
            String accessCredentialCiphertext,
            String refreshCredentialCiphertext,
            Instant now
    ) {
        return ContentSourceConnection.create(
                authorization.workspaceId(),
                authorization.provider(),
                accessCredentialCiphertext,
                refreshCredentialCiphertext,
                authorizedContentSource.externalSourceId(),
                authorizedContentSource.externalSourceName(),
                authorizedContentSource.externalSourceIcon(),
                authorizedContentSource.providerConnectionId(),
                authorizedContentSource.authorizationOwnerType(),
                authorizedContentSource.authorizationOwnerId(),
                authorizedContentSource.externalTemplateId(),
                authorizedContentSource.providerRequestId(),
                authorization.authorizingMemberId(),
                now
        );
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }
}

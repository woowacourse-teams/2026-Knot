package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ContentSourceAuthorizationContext;
import com.knot.backend.workspace.application.dto.result.ContentSourceAuthorizationResult;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceAuthorization;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class ContentSourceAuthorizationService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ContentSourceAuthorizationRepository authorizationRepository;
    private final ContentSourceStateGenerator stateGenerator;
    private final ContentSourceSecretProtector secretProtector;
    private final ContentSourceAuthorizationClient authorizationClient;
    private final ContentSourceAuthorizationSettings settings;
    private final Clock clock;

    @Transactional
    public ContentSourceAuthorizationResult start(
            Long workspaceId,
            long memberId,
            ContentSourceProvider provider
    ) {
        validateWorkspaceId(workspaceId);
        validateSupportedProvider(provider);
        workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateOwner(
                workspaceId,
                memberId
        );

        Instant now = currentTime();
        authorizationRepository.findPendingByWorkspaceIdAndProvider(
                workspaceId,
                provider
        )
                .ifPresent(authorization -> {
                    authorization.invalidate(now);
                    authorizationRepository.save(authorization);
                });

        String state = stateGenerator.generate();
        URI callbackUri = callbackUri();
        ContentSourceAuthorization authorization = ContentSourceAuthorization.create(
                workspaceId,
                provider,
                memberId,
                secretProtector.hashState(
                        provider,
                        state
                ),
                callbackUri,
                now,
                now.plus(stateTtl())
        );
        authorizationRepository.save(authorization);
        return new ContentSourceAuthorizationResult(
                authorizationClient.createAuthorizationUri(
                        provider,
                        state,
                        callbackUri
                )
        );
    }

    @Transactional
    public ContentSourceAuthorizationContext consume(
            ContentSourceProvider provider,
            String state
    ) {
        validateSupportedProvider(provider);
        String stateHash = secretProtector.hashState(
                provider,
                state
        );
        ContentSourceAuthorization authorization = authorizationRepository.findByProviderAndStateHashForUpdate(
                provider,
                stateHash
        )
                .orElseThrow(
                        () -> new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION)
                );
        authorization.consume(currentTime());
        ContentSourceAuthorization consumed = authorizationRepository.save(authorization);
        return new ContentSourceAuthorizationContext(
                consumed.getId(),
                consumed.getWorkspaceId(),
                consumed.getProvider(),
                consumed.getAuthorizingMemberId(),
                consumed.getCallbackUri()
        );
    }

    private void validateOwner(
            Long workspaceId,
            long memberId
    ) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                workspaceId,
                memberId,
                WorkspaceMemberRole.OWNER
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        }
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }

    private URI callbackUri() {
        URI callbackUri = settings.callbackUri();
        if (callbackUri == null || callbackUri.toString()
                .isBlank()) {
            throw configurationInvalid();
        }
        return callbackUri;
    }

    private Duration stateTtl() {
        Duration stateTtl = settings.stateTtl();
        if (stateTtl == null || stateTtl.isZero() || stateTtl.isNegative()) {
            throw configurationInvalid();
        }
        return stateTtl;
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private void validateSupportedProvider(ContentSourceProvider provider) {
        if (provider == null || authorizationClient.provider() == null) {
            throw configurationInvalid();
        }
        if (provider != authorizationClient.provider()) {
            throw new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_PROVIDER_MISMATCH);
        }
    }

    private ContentSourceException configurationInvalid() {
        return new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_CONFIGURATION_INVALID);
    }
}

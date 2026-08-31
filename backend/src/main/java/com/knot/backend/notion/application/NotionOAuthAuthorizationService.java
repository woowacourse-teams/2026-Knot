package com.knot.backend.notion.application;

import com.knot.backend.notion.application.dto.result.NotionOAuthAuthorizationContext;
import com.knot.backend.notion.application.dto.result.NotionOAuthAuthorizationResult;
import com.knot.backend.notion.domain.NotionErrorCode;
import com.knot.backend.notion.domain.NotionException;
import com.knot.backend.notion.domain.NotionOAuthAuthorization;
import com.knot.backend.notion.domain.NotionOAuthAuthorizationRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotionOAuthAuthorizationService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final NotionOAuthAuthorizationRepository authorizationRepository;
    private final NotionOAuthStateGenerator stateGenerator;
    private final NotionOAuthSecretProtector secretProtector;
    private final NotionOAuthClient oAuthClient;
    private final NotionOAuthSettings settings;
    private final Clock clock;

    @Transactional
    public NotionOAuthAuthorizationResult start(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateOwner(
                workspaceId,
                memberId
        );

        Instant now = currentTime();
        authorizationRepository.findPendingByWorkspaceId(workspaceId)
                .ifPresent(authorization -> {
                    authorization.invalidate(now);
                    authorizationRepository.save(authorization);
                });

        String state = stateGenerator.generate();
        URI callbackUri = callbackUri();
        NotionOAuthAuthorization authorization = NotionOAuthAuthorization.create(
                workspaceId,
                memberId,
                secretProtector.hashState(state),
                callbackUri,
                now,
                now.plus(stateTtl())
        );
        authorizationRepository.save(authorization);
        return new NotionOAuthAuthorizationResult(
                oAuthClient.createAuthorizationUri(
                        state,
                        callbackUri
                )
        );
    }

    @Transactional
    public NotionOAuthAuthorizationContext consume(String state) {
        String stateHash = secretProtector.hashState(state);
        NotionOAuthAuthorization authorization = authorizationRepository.findByStateHashForUpdate(stateHash)
                .orElseThrow(() -> new NotionException(NotionErrorCode.INVALID_NOTION_OAUTH_STATE));
        authorization.consume(currentTime());
        NotionOAuthAuthorization consumed = authorizationRepository.save(authorization);
        return new NotionOAuthAuthorizationContext(
                consumed.getId(),
                consumed.getWorkspaceId(),
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

    private NotionException configurationInvalid() {
        return new NotionException(NotionErrorCode.NOTION_OAUTH_CONFIGURATION_INVALID);
    }
}

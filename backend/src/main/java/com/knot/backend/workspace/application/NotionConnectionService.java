package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionOAuthAuthorizationContext;
import com.knot.backend.workspace.application.dto.result.NotionOAuthToken;
import com.knot.backend.workspace.domain.NotionConnection;
import com.knot.backend.workspace.domain.NotionConnectionRepository;
import com.knot.backend.workspace.domain.NotionErrorCode;
import com.knot.backend.workspace.domain.NotionException;
import com.knot.backend.workspace.domain.NotionOAuthAuthorizationRepository;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "notion.oauth", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class NotionConnectionService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final NotionOAuthAuthorizationRepository authorizationRepository;
    private final NotionConnectionRepository connectionRepository;
    private final NotionOAuthSecretProtector secretProtector;
    private final Clock clock;

    @Transactional
    public void connect(
            NotionOAuthAuthorizationContext authorization,
            NotionOAuthToken token
    ) {
        workspaceRepository.findByIdForUpdate(authorization.workspaceId())
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateLatestAuthorization(authorization);
        validateOwner(authorization);

        Instant now = currentTime();
        String accessTokenCiphertext = secretProtector.encrypt(
                authorization.workspaceId(),
                NotionOAuthCredentialKind.ACCESS_TOKEN,
                token.accessToken()
        );
        String refreshTokenCiphertext = encryptRefreshToken(
                authorization.workspaceId(),
                token.refreshToken()
        );
        NotionConnection connection = connectionRepository.findByWorkspaceIdForUpdate(authorization.workspaceId())
                .map(
                        existing -> replace(
                                existing,
                                authorization,
                                token,
                                accessTokenCiphertext,
                                refreshTokenCiphertext,
                                now
                        )
                )
                .orElseGet(
                        () -> create(
                                authorization,
                                token,
                                accessTokenCiphertext,
                                refreshTokenCiphertext,
                                now
                        )
                );
        connectionRepository.save(connection);
    }

    private void validateLatestAuthorization(NotionOAuthAuthorizationContext authorization) {
        if (authorizationRepository.existsNewerAuthorization(
                authorization.workspaceId(),
                authorization.authorizationId()
        )) {
            throw new NotionException(NotionErrorCode.STALE_NOTION_OAUTH_AUTHORIZATION);
        }
    }

    private void validateOwner(NotionOAuthAuthorizationContext authorization) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                authorization.workspaceId(),
                authorization.authorizingMemberId(),
                WorkspaceMemberRole.OWNER
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        }
    }

    private String encryptRefreshToken(
            Long workspaceId,
            String refreshToken
    ) {
        if (refreshToken == null) {
            return null;
        }
        return secretProtector.encrypt(
                workspaceId,
                NotionOAuthCredentialKind.REFRESH_TOKEN,
                refreshToken
        );
    }

    private NotionConnection replace(
            NotionConnection connection,
            NotionOAuthAuthorizationContext authorization,
            NotionOAuthToken token,
            String accessTokenCiphertext,
            String refreshTokenCiphertext,
            Instant now
    ) {
        connection.replace(
                accessTokenCiphertext,
                refreshTokenCiphertext,
                token.notionWorkspaceId(),
                token.notionWorkspaceName(),
                token.notionWorkspaceIcon(),
                token.botId(),
                token.ownerType(),
                token.ownerUserId(),
                token.duplicatedTemplateId(),
                token.requestId(),
                authorization.authorizingMemberId(),
                now
        );
        return connection;
    }

    private NotionConnection create(
            NotionOAuthAuthorizationContext authorization,
            NotionOAuthToken token,
            String accessTokenCiphertext,
            String refreshTokenCiphertext,
            Instant now
    ) {
        return NotionConnection.create(
                authorization.workspaceId(),
                accessTokenCiphertext,
                refreshTokenCiphertext,
                token.notionWorkspaceId(),
                token.notionWorkspaceName(),
                token.notionWorkspaceIcon(),
                token.botId(),
                token.ownerType(),
                token.ownerUserId(),
                token.duplicatedTemplateId(),
                token.requestId(),
                authorization.authorizingMemberId(),
                now
        );
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }
}

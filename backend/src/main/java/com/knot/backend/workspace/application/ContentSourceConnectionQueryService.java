package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ContentSourceConnectionStatusResult;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceConnectionStatus;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentSourceConnectionQueryService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ContentSourceConnectionRepository connectionRepository;

    public ContentSourceConnectionStatusResult findStatus(
            Long workspaceId,
            long memberId,
            ContentSourceProvider provider
    ) {
        validateWorkspaceId(workspaceId);
        validateProvider(provider);
        workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateMember(
                workspaceId,
                memberId
        );
        ContentSourceConnectionStatus status = connectionRepository.findByWorkspaceIdAndProvider(
                workspaceId,
                provider
        )
                .map(this::connectedStatus)
                .orElse(ContentSourceConnectionStatus.NOT_CONNECTED);
        return new ContentSourceConnectionStatusResult(status);
    }

    private ContentSourceConnectionStatus connectedStatus(ContentSourceConnection connection) {
        boolean authorizingMemberIsOwner = workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                connection.getWorkspaceId(),
                connection.getAuthorizingMemberId(),
                WorkspaceMemberRole.OWNER
        );
        return authorizingMemberIsOwner
                ? ContentSourceConnectionStatus.CONNECTED
                : ContentSourceConnectionStatus.REAUTH_REQUIRED;
    }

    private void validateMember(
            Long workspaceId,
            long memberId
    ) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }

    private void validateProvider(ContentSourceProvider provider) {
        if (provider == null) {
            throw new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_CONFIGURATION_INVALID);
        }
    }
}

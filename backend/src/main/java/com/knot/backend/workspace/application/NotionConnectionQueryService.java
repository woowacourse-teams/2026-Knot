package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionConnectionStatusResult;
import com.knot.backend.workspace.domain.NotionConnection;
import com.knot.backend.workspace.domain.NotionConnectionRepository;
import com.knot.backend.workspace.domain.NotionConnectionStatus;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "notion.oauth", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotionConnectionQueryService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final NotionConnectionRepository connectionRepository;

    public NotionConnectionStatusResult findStatus(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateMember(
                workspaceId,
                memberId
        );
        NotionConnectionStatus status = connectionRepository.findByWorkspaceId(workspaceId)
                .map(this::connectedStatus)
                .orElse(NotionConnectionStatus.NOT_CONNECTED);
        return new NotionConnectionStatusResult(status);
    }

    private NotionConnectionStatus connectedStatus(NotionConnection connection) {
        boolean authorizingMemberIsOwner = workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                connection.getWorkspaceId(),
                connection.getAuthorizingMemberId(),
                WorkspaceMemberRole.OWNER
        );
        return authorizingMemberIsOwner ? NotionConnectionStatus.CONNECTED : NotionConnectionStatus.REAUTH_REQUIRED;
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
}

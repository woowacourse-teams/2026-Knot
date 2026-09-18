package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationAcceptanceResult;

public record WorkspaceInvitationAcceptanceResponse(
        Long workspaceId,
        String workspaceName
) {

    public static WorkspaceInvitationAcceptanceResponse from(WorkspaceInvitationAcceptanceResult result) {
        return new WorkspaceInvitationAcceptanceResponse(
                result.workspaceId(),
                result.workspaceName()
        );
    }
}

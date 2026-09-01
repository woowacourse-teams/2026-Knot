package com.knot.backend.workspace.application.dto.result;

public record WorkspaceInvitationAcceptanceResult(
        Long workspaceId,
        String workspaceName,
        boolean created
) {
}

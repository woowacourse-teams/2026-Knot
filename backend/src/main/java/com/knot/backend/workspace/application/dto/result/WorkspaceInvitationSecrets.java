package com.knot.backend.workspace.application.dto.result;

public record WorkspaceInvitationSecrets(
        String code,
        String linkToken
) {
}

package com.knot.backend.workspace.application.dto.result;

import java.time.Instant;

public record WorkspaceInvitationResult(
        Long invitationId,
        String code,
        String linkToken,
        Instant expiresAt,
        boolean created
) {
}

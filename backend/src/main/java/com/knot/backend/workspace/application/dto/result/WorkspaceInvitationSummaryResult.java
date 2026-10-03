package com.knot.backend.workspace.application.dto.result;

import java.time.Instant;

public record WorkspaceInvitationSummaryResult(
        Long invitationId,
        Instant createdAt,
        Instant expiresAt
) {
}

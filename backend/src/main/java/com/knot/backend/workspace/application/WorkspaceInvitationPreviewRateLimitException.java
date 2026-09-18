package com.knot.backend.workspace.application;

import com.knot.backend.global.exception.ProjectException;
import com.knot.backend.global.exception.RetryAfterException;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;

public final class WorkspaceInvitationPreviewRateLimitException extends ProjectException
        implements
            RetryAfterException {
    private final long retryAfterSeconds;

    public WorkspaceInvitationPreviewRateLimitException(long retryAfterSeconds) {
        super(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_RATE_LIMIT_EXCEEDED);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

package com.knot.backend.workspace.application;

public interface WorkspaceInvitationPreviewRateLimiter {

    void consume(String remoteAddress);
}

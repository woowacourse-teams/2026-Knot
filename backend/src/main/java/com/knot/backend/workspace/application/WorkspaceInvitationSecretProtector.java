package com.knot.backend.workspace.application;

public interface WorkspaceInvitationSecretProtector {

    String hash(
            WorkspaceInvitationSecretKind kind,
            String secret
    );
}

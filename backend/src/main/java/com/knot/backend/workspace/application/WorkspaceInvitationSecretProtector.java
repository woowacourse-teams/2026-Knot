package com.knot.backend.workspace.application;

public interface WorkspaceInvitationSecretProtector {

    String hash(
            WorkspaceInvitationSecretKind kind,
            String secret
    );

    boolean matches(
            WorkspaceInvitationSecretKind kind,
            String secret,
            String expectedHash
    );

    String encrypt(
            Long workspaceId,
            WorkspaceInvitationSecretKind kind,
            String secret
    );

    String decrypt(
            Long workspaceId,
            WorkspaceInvitationSecretKind kind,
            String envelope
    );
}

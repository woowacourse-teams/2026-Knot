package com.knot.backend.workspace.application;

import com.knot.backend.workspace.domain.ContentSourceProvider;

public interface ContentSourceSecretProtector {

    String hashState(
            ContentSourceProvider provider,
            String state
    );

    String encrypt(
            Long workspaceId,
            ContentSourceProvider provider,
            ContentSourceCredentialKind kind,
            String secret
    );

    String decrypt(
            Long workspaceId,
            ContentSourceProvider provider,
            ContentSourceCredentialKind kind,
            String envelope
    );
}

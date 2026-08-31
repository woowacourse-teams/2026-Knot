package com.knot.backend.notion.application;

public interface NotionOAuthSecretProtector {

    String hashState(String state);

    String encrypt(
            Long workspaceId,
            NotionOAuthCredentialKind kind,
            String secret
    );

    String decrypt(
            Long workspaceId,
            NotionOAuthCredentialKind kind,
            String envelope
    );
}

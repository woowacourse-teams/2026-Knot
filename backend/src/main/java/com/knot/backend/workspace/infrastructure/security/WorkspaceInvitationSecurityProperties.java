package com.knot.backend.workspace.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.invitation")
public record WorkspaceInvitationSecurityProperties(
        String lookupHashKey,
        String encryptionKey
) {
}

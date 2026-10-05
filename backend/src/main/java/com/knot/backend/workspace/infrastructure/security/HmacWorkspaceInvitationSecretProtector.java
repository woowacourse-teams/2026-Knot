package com.knot.backend.workspace.infrastructure.security;

import com.knot.backend.workspace.application.WorkspaceInvitationSecretKind;
import com.knot.backend.workspace.application.WorkspaceInvitationSecretProtector;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacWorkspaceInvitationSecretProtector implements WorkspaceInvitationSecretProtector {
    private static final String HASH_ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec lookupHashKey;

    public HmacWorkspaceInvitationSecretProtector(WorkspaceInvitationSecurityProperties properties) {
        this.lookupHashKey = new SecretKeySpec(
                decodeConfiguredKey(properties == null ? null : properties.lookupHashKey()),
                HASH_ALGORITHM
        );
    }

    @Override
    public String hash(
            WorkspaceInvitationSecretKind kind,
            String secret
    ) {
        if (kind == null || secret == null || secret.isBlank()) {
            throw hashFailed();
        }
        try {
            Mac mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(lookupHashKey);
            return encode(mac.doFinal((kind.context() + ":" + secret).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new WorkspaceException(
                    WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED,
                    exception
            );
        }
    }

    private byte[] decodeConfiguredKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw configurationInvalid();
        }
        try {
            byte[] key = Base64.getUrlDecoder()
                    .decode(encodedKey);
            if (key.length != KEY_BYTES) {
                throw configurationInvalid();
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new WorkspaceException(
                    WorkspaceErrorCode.WORKSPACE_INVITATION_SECURITY_CONFIGURATION_INVALID,
                    exception
            );
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private WorkspaceException configurationInvalid() {
        return new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_SECURITY_CONFIGURATION_INVALID);
    }

    private WorkspaceException hashFailed() {
        return new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED);
    }
}

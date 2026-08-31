package com.knot.backend.workspace.infrastructure.security;

import com.knot.backend.workspace.application.WorkspaceInvitationSecretGenerator;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationSecrets;
import java.security.SecureRandom;
import java.util.Base64;

public class SecureWorkspaceInvitationSecretGenerator implements WorkspaceInvitationSecretGenerator {
    static final int CODE_LENGTH = 8;
    static final int LINK_TOKEN_BYTES = 32;
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom secureRandom;

    public SecureWorkspaceInvitationSecretGenerator() {
        this(new SecureRandom());
    }

    SecureWorkspaceInvitationSecretGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public WorkspaceInvitationSecrets generate() {
        return new WorkspaceInvitationSecrets(
                generateCode(),
                generateLinkToken()
        );
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    private String generateLinkToken() {
        byte[] token = new byte[LINK_TOKEN_BYTES];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(token);
    }
}

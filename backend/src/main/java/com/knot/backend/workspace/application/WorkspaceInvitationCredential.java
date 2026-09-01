package com.knot.backend.workspace.application;

import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.util.Locale;

public record WorkspaceInvitationCredential(
        WorkspaceInvitationSecretKind kind,
        String secret,
        boolean rateLimited
) {
    static final int CODE_LENGTH = 6;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public static WorkspaceInvitationCredential from(String tokenOrCode) {
        if (tokenOrCode == null || tokenOrCode.isBlank()) {
            throw previewNotFound();
        }
        String stripped = tokenOrCode.strip();
        if (stripped.length() == CODE_LENGTH) {
            return inviteCode(stripped);
        }
        return new WorkspaceInvitationCredential(
                WorkspaceInvitationSecretKind.LINK_TOKEN,
                tokenOrCode,
                false
        );
    }

    private static WorkspaceInvitationCredential inviteCode(String stripped) {
        String normalizedCode = stripped.toUpperCase(Locale.ROOT);
        return new WorkspaceInvitationCredential(
                WorkspaceInvitationSecretKind.INVITE_CODE,
                normalizedCode,
                true
        );
    }

    public void validate() {
        if (kind != WorkspaceInvitationSecretKind.INVITE_CODE) {
            return;
        }
        if (secret.length() != CODE_LENGTH) {
            throw previewNotFound();
        }
        for (int index = 0; index < secret.length(); index++) {
            if (CODE_ALPHABET.indexOf(secret.charAt(index)) < 0) {
                throw previewNotFound();
            }
        }
    }

    private static WorkspaceException previewNotFound() {
        return new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
    }
}

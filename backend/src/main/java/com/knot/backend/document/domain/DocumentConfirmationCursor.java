package com.knot.backend.document.domain;

import lombok.Getter;

@Getter
public class DocumentConfirmationCursor {
    private static final String VERSION = "1";
    private static final int FIELD_COUNT = 6;
    private static final int MAX_LENGTH = 512;
    private final long workspaceId;
    private final long documentId;
    private final long memberId;
    private final DocumentConfirmationState state;
    private final long targetMemberId;

    private DocumentConfirmationCursor(
            long workspaceId,
            long documentId,
            long memberId,
            DocumentConfirmationState state,
            long targetMemberId
    ) {
        validateScopeIdentifiers(
                workspaceId,
                documentId,
                memberId
        );
        validateBoundary(
                state,
                targetMemberId
        );
        this.workspaceId = workspaceId;
        this.documentId = documentId;
        this.memberId = memberId;
        this.state = state;
        this.targetMemberId = targetMemberId;
    }

    public static DocumentConfirmationCursor of(
            long workspaceId,
            long documentId,
            long memberId,
            DocumentConfirmationState state,
            long targetMemberId
    ) {
        return new DocumentConfirmationCursor(
                workspaceId,
                documentId,
                memberId,
                state,
                targetMemberId
        );
    }

    private static void validateScopeIdentifiers(
            long workspaceId,
            long documentId,
            long memberId
    ) {
        if (workspaceId <= 0 || documentId <= 0 || memberId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateBoundary(
            DocumentConfirmationState state,
            long targetMemberId
    ) {
        if (state == null || targetMemberId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

}

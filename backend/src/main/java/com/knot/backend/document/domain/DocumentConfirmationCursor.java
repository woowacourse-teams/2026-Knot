package com.knot.backend.document.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    public String encode() {
        String payload = VERSION + "|" + workspaceId + "|" + documentId + "|" + memberId + "|" + state.name() + "|"
                + targetMemberId;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static DocumentConfirmationCursor parse(
            String encoded,
            long workspaceId,
            long documentId,
            long memberId
    ) {
        try {
            validateEncodedCursor(encoded);
            String[] fields = new String(
                    Base64.getUrlDecoder()
                            .decode(encoded),
                    StandardCharsets.UTF_8
            ).split(
                    "\\|",
                    -1
            );
            validatePayload(fields);
            DocumentConfirmationCursor cursor = of(
                    Long.parseLong(fields[1]),
                    Long.parseLong(fields[2]),
                    Long.parseLong(fields[3]),
                    DocumentConfirmationState.valueOf(fields[4]),
                    Long.parseLong(fields[5])
            );
            cursor.validateQueryScope(
                    workspaceId,
                    documentId,
                    memberId
            );
            return cursor;
        } catch (IllegalArgumentException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
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

    private static void validateEncodedCursor(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_LENGTH) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validatePayload(String[] fields) {
        if (fields.length != FIELD_COUNT || !fields[0].equals(VERSION)) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private void validateQueryScope(
            long workspaceId,
            long documentId,
            long memberId
    ) {
        if (this.workspaceId != workspaceId || this.documentId != documentId || this.memberId != memberId) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}

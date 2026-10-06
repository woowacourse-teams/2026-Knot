package com.knot.backend.document.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import lombok.Getter;

@Getter
public class DocumentCursor {
    private final long workspaceId;
    private final long memberId;
    private final MyConfirmationState myConfirmation;
    private final Long recordingSessionId;
    private final Instant createdAt;
    private final long documentId;

    private DocumentCursor(
            long workspaceId,
            long memberId,
            MyConfirmationState myConfirmation,
            Long recordingSessionId,
            Instant createdAt,
            long documentId
    ) {
        if (workspaceId <= 0 || memberId <= 0 || documentId <= 0
                || recordingSessionId != null && recordingSessionId <= 0 || createdAt == null
                || createdAt.atOffset(ZoneOffset.UTC)
                        .getYear() < 1
                || createdAt.atOffset(ZoneOffset.UTC)
                        .getYear() > 9999) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
        this.workspaceId = workspaceId;
        this.memberId = memberId;
        this.myConfirmation = myConfirmation;
        this.recordingSessionId = recordingSessionId;
        this.createdAt = createdAt;
        this.documentId = documentId;
    }

    public static DocumentCursor of(
            long workspaceId,
            long memberId,
            MyConfirmationState myConfirmation,
            Long recordingSessionId,
            Instant createdAt,
            long documentId
    ) {
        return new DocumentCursor(
                workspaceId,
                memberId,
                myConfirmation,
                recordingSessionId,
                createdAt,
                documentId
        );
    }

}

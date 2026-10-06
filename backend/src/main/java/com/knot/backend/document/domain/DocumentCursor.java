package com.knot.backend.document.domain;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;
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

    public String encode() {
        String payload = "1|" + workspaceId + "|" + memberId + "|"
                + (myConfirmation == null ? "ALL" : myConfirmation.name()) + "|"
                + (recordingSessionId == null ? "ALL" : recordingSessionId) + "|" + createdAt + "|" + documentId;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static DocumentCursor parse(
            String encoded,
            long workspaceId,
            long memberId,
            MyConfirmationState myConfirmation,
            Long recordingSessionId
    ) {
        try {
            if (encoded == null || encoded.isBlank() || encoded.length() > 512) {
                throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
            }
            String[] values = new String(
                    Base64.getUrlDecoder()
                            .decode(encoded),
                    StandardCharsets.UTF_8
            ).split(
                    "\\|",
                    -1
            );
            if (values.length != 7 || !values[0].equals("1")) {
                throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
            }
            DocumentCursor cursor = of(
                    Long.parseLong(values[1]),
                    Long.parseLong(values[2]),
                    values[3].equals("ALL") ? null : MyConfirmationState.valueOf(values[3]),
                    values[4].equals("ALL") ? null : Long.valueOf(values[4]),
                    Instant.parse(values[5]),
                    Long.parseLong(values[6])
            );
            if (cursor.workspaceId != workspaceId || cursor.memberId != memberId
                    || cursor.myConfirmation != myConfirmation || !Objects.equals(
                            cursor.recordingSessionId,
                            recordingSessionId
                    )) {
                throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
            }
            return cursor;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}

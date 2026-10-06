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
    private static final String VERSION = "1";
    private static final String UNFILTERED = "ALL";
    private static final int PAYLOAD_FIELD_COUNT = 7;
    private static final int MAX_ENCODED_LENGTH = 512;
    private static final int MIN_YEAR = 1;
    private static final int MAX_YEAR = 9999;
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
        validateIdentifiers(
                workspaceId,
                memberId,
                documentId
        );
        validateRecordingSessionId(recordingSessionId);
        validateCreatedAt(createdAt);
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
        String payload = VERSION + "|" + workspaceId + "|" + memberId + "|" + encodeConfirmationFilter() + "|"
                + encodeRecordingFilter() + "|" + createdAt + "|" + documentId;
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
            validateEncodedCursor(encoded);
            String[] values = new String(
                    Base64.getUrlDecoder()
                            .decode(encoded),
                    StandardCharsets.UTF_8
            ).split(
                    "\\|",
                    -1
            );
            validatePayload(values);
            DocumentCursor cursor = of(
                    Long.parseLong(values[1]),
                    Long.parseLong(values[2]),
                    parseConfirmationFilter(values[3]),
                    parseRecordingFilter(values[4]),
                    Instant.parse(values[5]),
                    Long.parseLong(values[6])
            );
            cursor.validateQueryContext(
                    workspaceId,
                    memberId,
                    myConfirmation,
                    recordingSessionId
            );
            return cursor;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateIdentifiers(
            long workspaceId,
            long memberId,
            long documentId
    ) {
        if (workspaceId <= 0 || memberId <= 0 || documentId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateRecordingSessionId(Long recordingSessionId) {
        if (recordingSessionId != null && recordingSessionId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
        int year = createdAt.atOffset(ZoneOffset.UTC)
                .getYear();
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private String encodeConfirmationFilter() {
        if (myConfirmation == null) {
            return UNFILTERED;
        }
        return myConfirmation.name();
    }

    private String encodeRecordingFilter() {
        if (recordingSessionId == null) {
            return UNFILTERED;
        }
        return recordingSessionId.toString();
    }

    private static void validateEncodedCursor(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validatePayload(String[] values) {
        if (values.length != PAYLOAD_FIELD_COUNT || !values[0].equals(VERSION)) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static MyConfirmationState parseConfirmationFilter(String value) {
        if (value.equals(UNFILTERED)) {
            return null;
        }
        return MyConfirmationState.valueOf(value);
    }

    private static Long parseRecordingFilter(String value) {
        if (value.equals(UNFILTERED)) {
            return null;
        }
        return Long.valueOf(value);
    }

    private void validateQueryContext(
            long workspaceId,
            long memberId,
            MyConfirmationState myConfirmation,
            Long recordingSessionId
    ) {
        if (this.workspaceId != workspaceId || this.memberId != memberId) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
        if (this.myConfirmation != myConfirmation || !Objects.equals(
                this.recordingSessionId,
                recordingSessionId
        )) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}

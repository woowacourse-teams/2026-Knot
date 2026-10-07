package com.knot.backend.document.domain;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import lombok.Getter;

@Getter
public class DocumentGenerationJobCursor {
    private static final String VERSION = "1";
    private static final int FIELD_COUNT = 5;
    private static final int MAX_LENGTH = 512;
    private final long workspaceId;
    private final long memberId;
    private final Instant createdAt;
    private final long jobId;

    private DocumentGenerationJobCursor(
            long workspaceId,
            long memberId,
            Instant createdAt,
            long jobId
    ) {
        validateIdentifiers(
                workspaceId,
                memberId,
                jobId
        );
        validateCreatedAt(createdAt);
        this.workspaceId = workspaceId;
        this.memberId = memberId;
        this.createdAt = createdAt;
        this.jobId = jobId;
    }

    public static DocumentGenerationJobCursor of(
            long workspaceId,
            long memberId,
            Instant createdAt,
            long jobId
    ) {
        return new DocumentGenerationJobCursor(
                workspaceId,
                memberId,
                createdAt,
                jobId
        );
    }

    public String encode() {
        String payload = VERSION + "|" + workspaceId + "|" + memberId + "|" + createdAt + "|" + jobId;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static DocumentGenerationJobCursor parse(
            String encoded,
            long workspaceId,
            long memberId
    ) {
        validateEncoded(encoded);
        try {
            String[] fields = new String(
                    Base64.getUrlDecoder()
                            .decode(encoded),
                    StandardCharsets.UTF_8
            ).split(
                    "\\|",
                    -1
            );
            validatePayload(fields);
            DocumentGenerationJobCursor cursor = of(
                    Long.parseLong(fields[1]),
                    Long.parseLong(fields[2]),
                    Instant.parse(fields[3]),
                    Long.parseLong(fields[4])
            );
            cursor.validateScope(
                    workspaceId,
                    memberId
            );
            return cursor;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateEncoded(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_LENGTH) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validatePayload(String[] fields) {
        if (fields.length != FIELD_COUNT || !VERSION.equals(fields[0])) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateIdentifiers(
            long workspaceId,
            long memberId,
            long jobId
    ) {
        if (workspaceId <= 0 || memberId <= 0 || jobId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
        try {
            int year = createdAt.atOffset(ZoneOffset.UTC)
                    .getYear();
            if (year < 1 || year > 9999) {
                throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
            }
        } catch (DateTimeException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private void validateScope(
            long workspaceId,
            long memberId
    ) {
        if (this.workspaceId != workspaceId || this.memberId != memberId) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}

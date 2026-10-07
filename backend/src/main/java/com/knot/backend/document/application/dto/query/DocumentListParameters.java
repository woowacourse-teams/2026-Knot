package com.knot.backend.document.application.dto.query;

import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.MyConfirmationState;

public record DocumentListParameters(
        String cursor,
        int size,
        MyConfirmationState myConfirmation,
        Long recordingSessionId
) {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    public DocumentListParameters {
        validateSize(size);
        validateRecordingSessionId(recordingSessionId);
        validateCursor(cursor);
    }

    public static DocumentListParameters of(
            String cursor,
            Integer size,
            MyConfirmationState myConfirmation,
            Long recordingSessionId
    ) {
        return new DocumentListParameters(
                cursor,
                resolvePageSize(size),
                myConfirmation,
                recordingSessionId
        );
    }

    private static int resolvePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return size;
    }

    private static void validateSize(int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateRecordingSessionId(Long recordingSessionId) {
        if (recordingSessionId != null && recordingSessionId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateCursor(String cursor) {
        if (cursor != null && cursor.isBlank()) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}

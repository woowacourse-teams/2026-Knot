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
    public DocumentListParameters {
        if (size < 1 || size > 100 || recordingSessionId != null && recordingSessionId <= 0
                || cursor != null && cursor.isBlank()) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    public static DocumentListParameters of(
            String cursor,
            Integer size,
            MyConfirmationState myConfirmation,
            Long recordingSessionId
    ) {
        return new DocumentListParameters(
                cursor,
                size == null ? 50 : size,
                myConfirmation,
                recordingSessionId
        );
    }
}

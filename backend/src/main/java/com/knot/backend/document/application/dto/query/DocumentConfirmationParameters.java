package com.knot.backend.document.application.dto.query;

import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;

public record DocumentConfirmationParameters(
        String cursor,
        int size
) {
    private static final int DEFAULT_SIZE = 50;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    public DocumentConfirmationParameters {
        validateSize(size);
        validateCursor(cursor);
    }

    public static DocumentConfirmationParameters of(
            String cursor,
            Integer size
    ) {
        return new DocumentConfirmationParameters(
                cursor,
                resolveSize(size)
        );
    }

    private static int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return size;
    }

    private static void validateSize(int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateCursor(String cursor) {
        if (cursor != null && cursor.isBlank()) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}

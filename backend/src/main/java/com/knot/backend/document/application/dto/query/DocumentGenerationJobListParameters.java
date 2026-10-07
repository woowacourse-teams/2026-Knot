package com.knot.backend.document.application.dto.query;

import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;

public record DocumentGenerationJobListParameters(
        String cursor,
        int size
) {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public DocumentGenerationJobListParameters {
        validateSize(size);
        validateCursor(cursor);
    }

    public static DocumentGenerationJobListParameters of(
            String cursor,
            Integer size
    ) {
        return new DocumentGenerationJobListParameters(
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
        if (size < 1 || size > MAX_SIZE) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateCursor(String cursor) {
        if (cursor != null && cursor.isBlank()) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}

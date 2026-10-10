package com.knot.backend.search.application.dto.query;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;

public record SearchConversationListParameters(
        String cursor,
        int size
) {
    private static final int DEFAULT_SIZE = 20;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    public SearchConversationListParameters {
        validateSize(size);
        validateCursor(cursor);
    }

    public static SearchConversationListParameters of(
            String cursor,
            Integer size
    ) {
        return new SearchConversationListParameters(
                cursor,
                resolvePageSize(size)
        );
    }

    private static int resolvePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return size;
    }

    private static void validateSize(int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateCursor(String cursor) {
        if (cursor != null && cursor.isBlank()) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }
}

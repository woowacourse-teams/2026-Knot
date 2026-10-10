package com.knot.backend.search.application.dto.query;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;

public record SearchMessageListParameters(
        Integer beforeSequence,
        int size
) {
    private static final int DEFAULT_SIZE = 30;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    public SearchMessageListParameters {
        validateSize(size);
        validateBeforeSequence(beforeSequence);
    }

    public static SearchMessageListParameters of(
            Integer beforeSequence,
            Integer size
    ) {
        return new SearchMessageListParameters(
                beforeSequence,
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
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateBeforeSequence(Integer beforeSequence) {
        if (beforeSequence == null) {
            return;
        }
        if (beforeSequence <= 0) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }
}

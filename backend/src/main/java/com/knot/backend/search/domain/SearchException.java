package com.knot.backend.search.domain;

import com.knot.backend.global.exception.ProjectException;

public final class SearchException extends ProjectException {

    public SearchException(SearchErrorCode errorCode) {
        super(errorCode);
    }

    public SearchException(
            SearchErrorCode errorCode,
            Throwable cause
    ) {
        super(
                errorCode,
                cause
        );
    }

    public SearchErrorCode searchErrorCode() {
        return (SearchErrorCode) getErrorCode();
    }
}

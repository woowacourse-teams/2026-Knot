package com.knot.backend.search.domain;

import com.knot.backend.global.exception.ProjectException;

public final class SearchException extends ProjectException {

    public SearchException(SearchErrorCode errorCode) {
        super(errorCode);
    }
}

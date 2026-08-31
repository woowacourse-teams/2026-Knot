package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ProjectException;

public final class NotionException extends ProjectException {

    public NotionException(NotionErrorCode errorCode) {
        super(errorCode);
    }

    public NotionException(
            NotionErrorCode errorCode,
            Throwable cause
    ) {
        super(
                errorCode,
                cause
        );
    }
}

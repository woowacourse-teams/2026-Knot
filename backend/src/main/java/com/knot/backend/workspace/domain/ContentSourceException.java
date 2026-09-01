package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ProjectException;

public final class ContentSourceException extends ProjectException {

    public ContentSourceException(ContentSourceErrorCode errorCode) {
        super(errorCode);
    }

    public ContentSourceException(
            ContentSourceErrorCode errorCode,
            Throwable cause
    ) {
        super(
                errorCode,
                cause
        );
    }
}

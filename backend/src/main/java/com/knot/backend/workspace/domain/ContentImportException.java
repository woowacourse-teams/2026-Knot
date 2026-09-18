package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ProjectException;

public final class ContentImportException extends ProjectException {

    public ContentImportException(ContentImportErrorCode errorCode) {
        super(errorCode);
    }

    public ContentImportErrorCode contentImportErrorCode() {
        return (ContentImportErrorCode) getErrorCode();
    }
}

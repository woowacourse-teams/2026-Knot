package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ProjectException;

public final class NotionImportException extends ProjectException {

    public NotionImportException(NotionImportErrorCode errorCode) {
        super(errorCode);
    }
}

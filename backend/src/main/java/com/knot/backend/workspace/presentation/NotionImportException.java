package com.knot.backend.workspace.presentation;

import com.knot.backend.global.exception.ProjectException;
import com.knot.backend.workspace.domain.ContentImportException;

public final class NotionImportException extends ProjectException {

    private NotionImportException(
            NotionImportErrorCode errorCode,
            ContentImportException cause
    ) {
        super(
                errorCode,
                cause
        );
    }

    public static NotionImportException from(ContentImportException exception) {
        return new NotionImportException(
                NotionImportErrorCode.from(exception.contentImportErrorCode()),
                exception
        );
    }
}

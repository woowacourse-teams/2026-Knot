package com.knot.backend.workspace.presentation;

import com.knot.backend.global.exception.ProjectException;
import com.knot.backend.workspace.domain.ImportedPageException;

public final class NotionPageException extends ProjectException {

    private NotionPageException(
            NotionPageErrorCode errorCode,
            ImportedPageException cause
    ) {
        super(
                errorCode,
                cause
        );
    }

    public static NotionPageException from(ImportedPageException exception) {
        return new NotionPageException(
                NotionPageErrorCode.from(exception.importedPageErrorCode()),
                exception
        );
    }
}

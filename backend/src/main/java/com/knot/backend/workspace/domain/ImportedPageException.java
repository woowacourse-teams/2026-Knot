package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ProjectException;

public final class ImportedPageException extends ProjectException {

    public ImportedPageException(ImportedPageErrorCode errorCode) {
        super(errorCode);
    }

    public ImportedPageErrorCode importedPageErrorCode() {
        return (ImportedPageErrorCode) getErrorCode();
    }
}

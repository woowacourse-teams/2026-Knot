package com.knot.backend.document.domain;

import com.knot.backend.global.exception.ProjectException;

public final class DocumentException extends ProjectException {
    public DocumentException(DocumentErrorCode errorCode) {
        super(errorCode);
    }
}

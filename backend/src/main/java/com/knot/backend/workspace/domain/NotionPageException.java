package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ProjectException;

public final class NotionPageException extends ProjectException {

    public NotionPageException(NotionPageErrorCode errorCode) {
        super(errorCode);
    }
}

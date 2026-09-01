package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ProjectException;

public final class WorkspaceException extends ProjectException {

    public WorkspaceException(WorkspaceErrorCode errorCode) {
        super(errorCode);
    }

    public WorkspaceException(
            WorkspaceErrorCode errorCode,
            Throwable cause
    ) {
        super(
                errorCode,
                cause
        );
    }
}

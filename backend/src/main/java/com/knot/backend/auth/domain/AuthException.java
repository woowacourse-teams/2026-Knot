package com.knot.backend.auth.domain;

import com.knot.backend.global.exception.ProjectException;

public class AuthException extends ProjectException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(
            AuthErrorCode errorCode,
            Throwable cause
    ) {
        super(
                errorCode,
                cause
        );
    }
}

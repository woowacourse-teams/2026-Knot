package com.knot.backend.global.exception;

public final class LlmException extends ProjectException {

    public LlmException(LlmErrorCode errorCode) {
        super(errorCode);
    }
}

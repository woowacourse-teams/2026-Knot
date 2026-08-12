package com.knot.backend.global.exception;

import java.io.Serial;
import java.util.Objects;
import lombok.Getter;

@Getter
public abstract class ProjectException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    protected ProjectException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    protected ProjectException(ErrorCode errorCode, Throwable cause) {
        super(requireErrorCode(errorCode).getMessage(), cause);
        this.errorCode = errorCode;
    }

    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        return Objects.requireNonNull(errorCode, "errorCode");
    }
}

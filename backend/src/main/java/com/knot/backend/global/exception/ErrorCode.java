package com.knot.backend.global.exception;

public interface ErrorCode {

    ErrorCategory getCategory();

    String getCode();

    String getMessage();
}

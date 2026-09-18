package com.knot.backend.global.exception;

public interface RetryAfterException {

    long getRetryAfterSeconds();
}

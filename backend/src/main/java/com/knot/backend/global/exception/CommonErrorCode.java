package com.knot.backend.global.exception;

public enum CommonErrorCode implements ErrorCode {
    VALIDATION_ERROR(
            ErrorCategory.INVALID_INPUT,
            "VALIDATION_ERROR",
            "입력값이 올바르지 않습니다"
    ),

    INVALID_REQUEST_BODY(
            ErrorCategory.INVALID_INPUT,
            "INVALID_REQUEST_BODY",
            "요청 본문 형식이 올바르지 않습니다"
    ),

    INVALID_PARAMETER(
            ErrorCategory.INVALID_INPUT,
            "INVALID_PARAMETER",
            "요청 파라미터 형식이 올바르지 않습니다"
    ),

    MISSING_PARAMETER(
            ErrorCategory.INVALID_INPUT,
            "MISSING_PARAMETER",
            "필수 요청 파라미터가 누락되었습니다"
    ),

    FORBIDDEN(
            ErrorCategory.FORBIDDEN,
            "FORBIDDEN",
            "요청 권한이 없습니다"
    ),

    INTERNAL_SERVER_ERROR(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "서버 오류가 발생했습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    CommonErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }

    @Override
    public ErrorCategory getCategory() {
        return category;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

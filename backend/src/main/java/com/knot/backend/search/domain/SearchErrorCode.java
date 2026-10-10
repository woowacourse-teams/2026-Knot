package com.knot.backend.search.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchErrorCode implements ErrorCode {
    INVALID_PARAMETER(
            ErrorCategory.INVALID_INPUT,
            "INVALID_PARAMETER",
            "요청 값이 올바르지 않습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;
}

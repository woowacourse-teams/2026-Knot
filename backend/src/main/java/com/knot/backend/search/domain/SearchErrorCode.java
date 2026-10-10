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
    ),
    SEARCH_ACCESS_DENIED(
            ErrorCategory.FORBIDDEN,
            "SEARCH_ACCESS_DENIED",
            "탐색 대화에 접근할 수 없습니다"
    ),
    CONVERSATION_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "CONVERSATION_NOT_FOUND",
            "탐색 대화를 찾을 수 없습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;
}

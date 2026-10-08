package com.knot.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LlmErrorCode implements ErrorCode {

    LLM_INVALID_CONFIGURATION(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_INVALID_CONFIGURATION",
            "LLM 연결 설정이 올바르지 않습니다"
    ),
    LLM_INVALID_REQUEST(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_INVALID_REQUEST",
            "LLM 요청 정보가 올바르지 않습니다"
    ),
    LLM_AUTHENTICATION_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_AUTHENTICATION_FAILED",
            "LLM 공급자 인증에 실패했습니다"
    ),
    LLM_RATE_LIMITED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_RATE_LIMITED",
            "LLM 공급자 요청 한도를 초과했습니다"
    ),
    LLM_UNAVAILABLE(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_UNAVAILABLE",
            "LLM 공급자에 연결할 수 없습니다"
    ),
    LLM_TIMEOUT(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_TIMEOUT",
            "LLM 응답 대기 시간이 초과되었습니다"
    ),
    LLM_CALL_INTERRUPTED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_CALL_INTERRUPTED",
            "LLM 호출 대기가 중단되었습니다"
    ),
    LLM_REQUEST_REJECTED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_REQUEST_REJECTED",
            "LLM 공급자가 요청을 거절했습니다"
    ),
    LLM_INPUT_LIMIT_EXCEEDED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_INPUT_LIMIT_EXCEEDED",
            "LLM 입력 한도를 초과했습니다"
    ),
    LLM_OUTPUT_LIMIT_EXCEEDED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_OUTPUT_LIMIT_EXCEEDED",
            "LLM 출력 한도를 초과했습니다"
    ),
    LLM_INVALID_RESPONSE(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_INVALID_RESPONSE",
            "LLM 공급자 응답이 올바르지 않습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;
}

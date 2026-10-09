package com.knot.backend.document.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentErrorCode implements ErrorCode {
    INVALID_PARAMETER(
            ErrorCategory.INVALID_INPUT,
            "INVALID_PARAMETER",
            "요청 값이 올바르지 않습니다"
    ),
    DOCUMENT_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "DOCUMENT_NOT_FOUND",
            "문서를 찾을 수 없습니다"
    ),
    TRANSCRIPT_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "TRANSCRIPT_NOT_FOUND",
            "문서에 연결된 원문을 찾을 수 없습니다"
    ),
    INVALID_TRANSCRIPT_DATA(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "INVALID_TRANSCRIPT_DATA",
            "문서 원문을 불러올 수 없습니다"
    ),
    CONFIRMATION_NOT_REQUIRED(
            ErrorCategory.CONFLICT,
            "CONFIRMATION_NOT_REQUIRED",
            "문서 확인 대상이 아닙니다"
    ),
    DOCUMENT_GENERATION_JOB_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "DOCUMENT_GENERATION_JOB_NOT_FOUND",
            "문서 생성 작업을 찾을 수 없습니다"
    ),
    RETRY_NOT_ALLOWED(
            ErrorCategory.CONFLICT,
            "RETRY_NOT_ALLOWED",
            "문서 생성 작업을 재시도할 수 없습니다"
    ),
    INVALID_DOCUMENT_DATA(
            ErrorCategory.INVALID_INPUT,
            "INVALID_DOCUMENT_DATA",
            "문서 저장 정보가 올바르지 않습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;
}

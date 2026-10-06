package com.knot.backend.document.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentErrorCode implements ErrorCode {
    DOCUMENT_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "DOCUMENT_NOT_FOUND",
            "문서를 찾을 수 없습니다"
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

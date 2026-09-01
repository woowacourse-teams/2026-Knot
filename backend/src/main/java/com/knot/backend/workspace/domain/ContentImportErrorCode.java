package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum ContentImportErrorCode implements ErrorCode {
    INVALID_CONTENT_IMPORT_RUN_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CONTENT_IMPORT_RUN_ID",
            "콘텐츠 가져오기 실행 ID가 올바르지 않습니다"
    ),

    INVALID_CONTENT_IMPORT_RUN(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CONTENT_IMPORT_RUN",
            "콘텐츠 가져오기 실행 정보가 올바르지 않습니다"
    ),

    CONTENT_SOURCE_CONNECTION_NOT_CONNECTED(
            ErrorCategory.CONFLICT,
            "CONTENT_SOURCE_CONNECTION_NOT_CONNECTED",
            "콘텐츠 소스 연결이 필요합니다"
    ),

    CONTENT_SOURCE_CONNECTION_REAUTHENTICATION_REQUIRED(
            ErrorCategory.CONFLICT,
            "CONTENT_SOURCE_CONNECTION_REAUTHENTICATION_REQUIRED",
            "콘텐츠 소스 연결 재인증이 필요합니다"
    ),

    CONTENT_IMPORT_RUN_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "CONTENT_IMPORT_RUN_NOT_FOUND",
            "콘텐츠 가져오기 실행을 찾을 수 없습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    ContentImportErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}

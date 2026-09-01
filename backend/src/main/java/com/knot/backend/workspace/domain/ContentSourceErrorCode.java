package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum ContentSourceErrorCode implements ErrorCode {
    INVALID_CONTENT_SOURCE_AUTHORIZATION(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CONTENT_SOURCE_AUTHORIZATION",
            "콘텐츠 소스 인증 정보가 올바르지 않습니다"
    ),

    EXPIRED_CONTENT_SOURCE_AUTHORIZATION(
            ErrorCategory.INVALID_INPUT,
            "EXPIRED_CONTENT_SOURCE_AUTHORIZATION",
            "콘텐츠 소스 인증 정보가 만료되었습니다"
    ),

    STALE_CONTENT_SOURCE_AUTHORIZATION(
            ErrorCategory.CONFLICT,
            "STALE_CONTENT_SOURCE_AUTHORIZATION",
            "더 최신 콘텐츠 소스 인증 흐름이 있습니다"
    ),

    CONTENT_SOURCE_AUTHORIZATION_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "CONTENT_SOURCE_AUTHORIZATION_FAILED",
            "콘텐츠 소스 인증에 실패했습니다"
    ),

    CONTENT_SOURCE_CONFIGURATION_INVALID(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "CONTENT_SOURCE_CONFIGURATION_INVALID",
            "콘텐츠 소스 설정이 올바르지 않습니다"
    ),

    CONTENT_SOURCE_SECRET_PROTECTION_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "CONTENT_SOURCE_SECRET_PROTECTION_FAILED",
            "콘텐츠 소스 비밀값을 보호할 수 없습니다"
    ),

    CONTENT_SOURCE_PROVIDER_MISMATCH(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "CONTENT_SOURCE_PROVIDER_MISMATCH",
            "콘텐츠 소스 공급자 정보가 일치하지 않습니다"
    ),

    INVALID_CONTENT_SOURCE_CONNECTION(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CONTENT_SOURCE_CONNECTION",
            "콘텐츠 소스 연결 정보가 올바르지 않습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    ContentSourceErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}

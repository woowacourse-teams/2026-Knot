package com.knot.backend.search.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum SearchErrorCode implements ErrorCode {
    INVALID_SEARCH_WORKSPACE_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_SEARCH_WORKSPACE_ID",
            "검색 워크스페이스 ID가 올바르지 않습니다"
    ),

    INVALID_SEARCH_QUERY(
            ErrorCategory.INVALID_INPUT,
            "INVALID_SEARCH_QUERY",
            "검색 질문이 올바르지 않습니다"
    ),

    SEARCH_IMPORT_NOT_READY(
            ErrorCategory.CONFLICT,
            "SEARCH_IMPORT_NOT_READY",
            "문서 동기화가 완료된 후 검색할 수 있습니다"
    ),

    SEARCH_CONFIGURATION_INVALID(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "SEARCH_CONFIGURATION_INVALID",
            "문서 검색 설정이 올바르지 않습니다"
    ),

    SEARCH_PROVIDER_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "SEARCH_PROVIDER_FAILED",
            "문서 임베딩 생성에 실패했습니다"
    ),

    SEARCH_INDEX_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "SEARCH_INDEX_FAILED",
            "문서 검색 색인에 실패했습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    SearchErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}

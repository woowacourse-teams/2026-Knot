package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum ImportedPageErrorCode implements ErrorCode {
    INVALID_IMPORTED_PAGE(
            ErrorCategory.INVALID_INPUT,
            "INVALID_IMPORTED_PAGE",
            "가져온 페이지 정보가 올바르지 않습니다"
    ),

    IMPORTED_PAGE_TREE_INVALID(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "IMPORTED_PAGE_TREE_INVALID",
            "가져온 페이지 트리를 조회할 수 없습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    ImportedPageErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}

package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum NotionPageErrorCode implements ErrorCode {
    INVALID_NOTION_PAGE(
            ErrorCategory.INVALID_INPUT,
            "INVALID_NOTION_PAGE",
            "Notion Page 정보가 올바르지 않습니다"
    ),

    NOTION_PAGE_TREE_INVALID(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "NOTION_PAGE_TREE_INVALID",
            "Notion Page Tree를 조회할 수 없습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    NotionPageErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}

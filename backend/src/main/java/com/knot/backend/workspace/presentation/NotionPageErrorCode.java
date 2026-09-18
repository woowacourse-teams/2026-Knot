package com.knot.backend.workspace.presentation;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import com.knot.backend.workspace.domain.ImportedPageErrorCode;
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

    public static NotionPageErrorCode from(ImportedPageErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_IMPORTED_PAGE -> INVALID_NOTION_PAGE;
            case IMPORTED_PAGE_TREE_INVALID -> NOTION_PAGE_TREE_INVALID;
        };
    }
}

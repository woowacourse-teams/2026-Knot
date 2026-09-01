package com.knot.backend.workspace.presentation;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import com.knot.backend.workspace.domain.ContentImportErrorCode;
import lombok.Getter;

@Getter
public enum NotionImportErrorCode implements ErrorCode {
    INVALID_NOTION_IMPORT_RUN_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_NOTION_IMPORT_RUN_ID",
            "Notion Import 실행 ID가 올바르지 않습니다"
    ),

    INVALID_NOTION_IMPORT_RUN(
            ErrorCategory.INVALID_INPUT,
            "INVALID_NOTION_IMPORT_RUN",
            "Notion Import 실행 정보가 올바르지 않습니다"
    ),

    NOTION_IMPORT_RUN_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "NOTION_IMPORT_RUN_NOT_FOUND",
            "Notion Import 실행을 찾을 수 없습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    NotionImportErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }

    public static NotionImportErrorCode from(ContentImportErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_CONTENT_IMPORT_RUN_ID -> INVALID_NOTION_IMPORT_RUN_ID;
            case INVALID_CONTENT_IMPORT_RUN -> INVALID_NOTION_IMPORT_RUN;
            case CONTENT_IMPORT_RUN_NOT_FOUND -> NOTION_IMPORT_RUN_NOT_FOUND;
        };
    }
}

package com.knot.backend.notion.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum NotionErrorCode implements ErrorCode {
    INVALID_NOTION_OAUTH_STATE(
            ErrorCategory.INVALID_INPUT,
            "INVALID_NOTION_OAUTH_STATE",
            "Notion OAuth state가 올바르지 않습니다"
    ),

    EXPIRED_NOTION_OAUTH_STATE(
            ErrorCategory.INVALID_INPUT,
            "EXPIRED_NOTION_OAUTH_STATE",
            "Notion OAuth state가 만료되었습니다"
    ),

    STALE_NOTION_OAUTH_AUTHORIZATION(
            ErrorCategory.CONFLICT,
            "STALE_NOTION_OAUTH_AUTHORIZATION",
            "더 최신 Notion OAuth 인증 흐름이 있습니다"
    ),

    NOTION_OAUTH_TOKEN_EXCHANGE_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "NOTION_OAUTH_TOKEN_EXCHANGE_FAILED",
            "Notion OAuth 토큰 교환에 실패했습니다"
    ),

    NOTION_OAUTH_CONFIGURATION_INVALID(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "NOTION_OAUTH_CONFIGURATION_INVALID",
            "Notion OAuth 설정이 올바르지 않습니다"
    ),

    NOTION_OAUTH_SECRET_PROTECTION_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "NOTION_OAUTH_SECRET_PROTECTION_FAILED",
            "Notion OAuth 비밀값을 보호할 수 없습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    NotionErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}

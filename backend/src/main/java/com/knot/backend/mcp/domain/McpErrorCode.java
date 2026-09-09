package com.knot.backend.mcp.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum McpErrorCode implements ErrorCode {
    MCP_ORIGIN_NOT_ALLOWED(
            ErrorCategory.FORBIDDEN,
            "MCP_ORIGIN_NOT_ALLOWED",
            "브라우저에서는 MCP 엔드포인트를 쓸 수 없습니다"
    ),

    MCP_PROTOCOL_VERSION_UNSUPPORTED(
            ErrorCategory.INVALID_INPUT,
            "MCP_PROTOCOL_VERSION_UNSUPPORTED",
            "지원하지 않는 MCP 프로토콜 버전입니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    McpErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}

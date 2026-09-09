package com.knot.backend.mcp.domain;

import com.knot.backend.global.exception.ProjectException;

public final class McpException extends ProjectException {

    public McpException(McpErrorCode errorCode) {
        super(errorCode);
    }

    public McpErrorCode mcpErrorCode() {
        return (McpErrorCode) getErrorCode();
    }
}

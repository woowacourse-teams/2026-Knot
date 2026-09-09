package com.knot.backend.mcp.application;

/** 도구 인자가 입력 스키마와 다를 때. JSON-RPC {@code -32602 Invalid params}로 돌려준다(스펙의 프로토콜 오류). */
public final class McpToolInputException extends RuntimeException {

    public McpToolInputException(String message) {
        super(message);
    }
}

package com.knot.backend.mcp.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

/** JSON-RPC 2.0 응답 모양과 표준 오류 코드(MCP 기본 프로토콜은 JSON-RPC 2.0 위에 있다). */
public final class JsonRpc {
    public static final String VERSION = "2.0";
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    private JsonRpc() {
    }

    public record Success(
            String jsonrpc,
            JsonNode id,
            Object result
    ) {
    }

    public record Failure(
            String jsonrpc,
            JsonNode id,
            Error error
    ) {
    }

    public record Error(
            int code,
            String message
    ) {
    }

    public static Success success(
            JsonNode id,
            Object result
    ) {
        return new Success(
                VERSION,
                id,
                result
        );
    }

    /** id를 알 수 없는 오류(파싱 실패 등)는 JSON-RPC 규약대로 {@code "id": null}로 돌려준다. */
    public static Failure failure(
            JsonNode id,
            int code,
            String message
    ) {
        return new Failure(
                VERSION,
                id == null ? NullNode.getInstance() : id,
                new Error(
                        code,
                        message
                )
        );
    }
}

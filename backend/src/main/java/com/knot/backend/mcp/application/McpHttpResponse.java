package com.knot.backend.mcp.application;

/**
 * MCP Streamable HTTP 응답. 본문이 null이면 202 Accepted(알림·클라이언트 응답)처럼 본문 없는 응답이다.
 *
 * @param status
 *            HTTP 상태 코드
 * @param body
 *            JSON-RPC 응답 객체 또는 null
 */
public record McpHttpResponse(
        int status,
        Object body
) {

    public static McpHttpResponse ok(Object body) {
        return new McpHttpResponse(
                200,
                body
        );
    }

    public static McpHttpResponse accepted() {
        return new McpHttpResponse(
                202,
                null
        );
    }

    public static McpHttpResponse badRequest(Object body) {
        return new McpHttpResponse(
                400,
                body
        );
    }
}

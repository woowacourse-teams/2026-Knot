package com.knot.backend.mcp.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 도구 호출 결과. 데스크톱 로컬 MCP 서버의 {@code toCallToolResult}와 같은 모양으로 직렬화한다 — 텍스트 한 조각,
 * 선택적 {@code structuredContent}, 오류면 {@code isError: true} + "코드: 문구" 텍스트(로드맵 Q49).
 *
 * @param text
 *            에이전트가 읽는 본문
 * @param structuredContent
 *            원본 필드. 없으면 null
 * @param isError
 *            도구 실행 오류 여부
 * @param logStatus
 *            로그용 상태("ok" 또는 "error:<코드>"). 응답에는 싣지 않는다
 */
public record McpToolResult(
        String text,
        Object structuredContent,
        boolean isError,
        String logStatus
) {

    public static McpToolResult ok(
            String text,
            Object structuredContent
    ) {
        return new McpToolResult(
                text,
                structuredContent,
                false,
                "ok"
        );
    }

    public static McpToolResult error(
            String code,
            String message
    ) {
        return new McpToolResult(
                code + ": " + message,
                null,
                true,
                "error:" + code
        );
    }

    public static McpToolResult error(
            String code,
            String text,
            Object structuredContent
    ) {
        return new McpToolResult(
                text,
                structuredContent,
                true,
                "error:" + code
        );
    }

    /** MCP {@code tools/call} 결과 객체. */
    public Map<String, Object> toCallToolResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "content",
                List.of(
                        Map.of(
                                "type",
                                "text",
                                "text",
                                text
                        )
                )
        );
        if (structuredContent != null) {
            result.put(
                    "structuredContent",
                    structuredContent
            );
        }
        if (isError) {
            result.put(
                    "isError",
                    true
            );
        }
        return result;
    }
}

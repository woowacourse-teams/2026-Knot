package com.knot.backend.mcp.application;

import java.util.List;

/**
 * 이 서버가 받는 MCP 프로토콜 개정판(기획서 6.6, 로드맵 Q53). 데스크톱 로컬 MCP 서버가 쓰는 공식 TypeScript SDK
 * 1.30.0의 최신 개정판(2025-11-25)과, Claude Code·Codex CLI·Gemini CLI가 보내는 initialize 세대 개정판을
 * 포함한다. 클라이언트가 모르는 개정판을 요청하면 최신 개정판으로 답한다(스펙: 서버가 지원하는 다른 버전으로 응답).
 */
public final class McpProtocolVersions {
    public static final String LATEST = "2025-11-25";
    public static final List<String> SUPPORTED = List.of(
            LATEST,
            "2025-06-18",
            "2025-03-26"
    );

    private McpProtocolVersions() {
    }

    public static boolean isSupported(String version) {
        return version != null && SUPPORTED.contains(version);
    }

    public static String negotiate(String requested) {
        return isSupported(requested) ? requested : LATEST;
    }
}

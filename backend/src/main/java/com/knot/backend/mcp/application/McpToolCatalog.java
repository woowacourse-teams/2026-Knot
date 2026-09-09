package com.knot.backend.mcp.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 원격 MCP 서버가 공개하는 도구 정의(기획서 6.6, 로드맵 A13). 이름·설명·입력 스키마·annotations는 데스크톱 로컬 MCP
 * 서버({@code desktop/src/mcp/server.ts})와 같은 문구를 써서 스킬({@code SKILL.md})이 두 서버에 그대로 통한다.
 */
public final class McpToolCatalog {
    public static final String LIST_WORKSPACES = "list_workspaces";
    public static final String SEARCH_DOCUMENTS = "search_documents";
    public static final int MAX_QUERY_LENGTH = 10_000;

    private static final Map<String, Object> READ_ONLY_ANNOTATIONS = orderedMap(
            "readOnlyHint",
            true,
            "openWorldHint",
            false
    );

    private static final List<Map<String, Object>> DEFINITIONS = List.of(
            tool(
                    LIST_WORKSPACES,
                    "Knot 워크스페이스 목록",
                    "로그인한 사용자가 속한 Knot 워크스페이스 목록을 돌려준다. search_documents에 넘길 workspaceId를 고를 때 쓴다.",
                    orderedMap(
                            "type",
                            "object",
                            "properties",
                            Map.of(),
                            "additionalProperties",
                            false
                    )
            ),
            tool(
                    SEARCH_DOCUMENTS,
                    "Knot 팀 문서 검색",
                    "Knot 워크스페이스에 동기화된 팀 문서(회의록·결정 기록·기획서)에서 질문과 관련된 근거 청크를 최대 8개 찾는다. "
                            + "결과 앞머리의 근거 규칙 문장을 지켜 답하고, 출처는 결과의 `문서 링크`로 표시한다. 아무것도 저장하지 않으므로 "
                            + "한 질문에 여러 번 검색해도 된다.",
                    orderedMap(
                            "type",
                            "object",
                            "properties",
                            orderedMap(
                                    "query",
                                    orderedMap(
                                            "type",
                                            "string",
                                            "minLength",
                                            1,
                                            "maxLength",
                                            MAX_QUERY_LENGTH,
                                            "description",
                                            "검색할 질문. 사용자의 질문을 그대로 넣는다(1~10,000자)"
                                    ),
                                    "workspaceId",
                                    orderedMap(
                                            "type",
                                            "integer",
                                            "minimum",
                                            1,
                                            "description",
                                            "검색할 Knot 워크스페이스 ID. 하나뿐이면 생략 가능. 여럿이면 list_workspaces로 고른다"
                                    )
                            ),
                            "required",
                            List.of("query"),
                            "additionalProperties",
                            false
                    )
            )
    );

    private McpToolCatalog() {
    }

    /** {@code tools/list} 응답의 {@code tools} 배열. */
    public static List<Map<String, Object>> definitions() {
        return DEFINITIONS;
    }

    public static boolean contains(String name) {
        return LIST_WORKSPACES.equals(name) || SEARCH_DOCUMENTS.equals(name);
    }

    private static Map<String, Object> tool(
            String name,
            String title,
            String description,
            Map<String, Object> inputSchema
    ) {
        return orderedMap(
                "name",
                name,
                "title",
                title,
                "description",
                description,
                "inputSchema",
                inputSchema,
                "annotations",
                READ_ONLY_ANNOTATIONS
        );
    }

    /** 직렬화 순서를 고정하기 위한 LinkedHashMap 빌더(키·값 번갈아). */
    private static Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            map.put(
                    (String) keyValues[index],
                    keyValues[index + 1]
            );
        }
        return map;
    }
}

package com.knot.backend.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.chat.application.WorkspaceSearchService;
import com.knot.backend.chat.application.dto.result.WorkspaceSearchResult;
import com.knot.backend.search.domain.SearchResultStatus;
import com.knot.backend.workspace.application.WorkspaceQueryService;
import com.knot.backend.workspace.application.dto.result.WorkspaceListItemResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceListResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

/**
 * 원격 MCP 서버의 JSON-RPC 처리(기획서 6.6, 로드맵 A13). 무상태 서버라 세션을 만들지 않고, 알림·클라이언트 응답은
 * 202로 받기만 한다.
 */
class McpRequestHandlerTest {
    private static final long MEMBER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkspaceQueryService workspaceQueryService = Mockito.mock(WorkspaceQueryService.class);
    private final WorkspaceSearchService workspaceSearchService = Mockito.mock(WorkspaceSearchService.class);
    private final McpRequestHandler handler = new McpRequestHandler(
            objectMapper,
            new McpToolExecutor(
                    workspaceQueryService,
                    workspaceSearchService
            )
    );

    @Test
    @DisplayName("initialize에 서버 정보·도구 capability·instructions를 담아 답한다")
    void handle_success_initialize() {
        // given
        String body = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}
                """;

        // when
        McpHttpResponse response = handler.handle(
                body,
                MEMBER_ID
        );

        // then
        assertThat(response.status()).isEqualTo(200);
        Map<String, Object> result = successResult(response);
        assertThat(result).containsEntry(
                "protocolVersion",
                "2025-06-18"
        );
        assertThat(result).containsEntry(
                "instructions",
                McpServerInstructions.TEXT
        );
        assertThat(result.get("serverInfo")).isEqualTo(
                Map.of(
                        "name",
                        McpRequestHandler.SERVER_NAME,
                        "title",
                        McpRequestHandler.SERVER_TITLE,
                        "version",
                        McpRequestHandler.SERVER_VERSION
                )
        );
    }

    @Test
    @DisplayName("모르는 프로토콜 개정판을 요청하면 서버가 지원하는 최신 개정판으로 답한다")
    void handle_success_initializeNegotiatesUnknownVersion() {
        // given
        String body = """
                {"jsonrpc":"2.0","id":"a","method":"initialize","params":{"protocolVersion":"1999-01-01"}}
                """;

        // when
        McpHttpResponse response = handler.handle(
                body,
                MEMBER_ID
        );

        // then
        assertThat(successResult(response)).containsEntry(
                "protocolVersion",
                McpProtocolVersions.LATEST
        );
    }

    @Test
    @DisplayName("tools/list는 데스크톱 로컬 서버와 같은 도구 두 개를 돌려준다(show_answer는 없다)")
    void handle_success_toolsList() {
        // given
        String body = """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """;

        // when
        McpHttpResponse response = handler.handle(
                body,
                MEMBER_ID
        );

        // then
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) successResult(response).get("tools");
        assertThat(tools).extracting(tool -> tool.get("name"))
                .containsExactly(
                        McpToolCatalog.LIST_WORKSPACES,
                        McpToolCatalog.SEARCH_DOCUMENTS
                );
    }

    @Test
    @DisplayName("tools/call은 로그인한 회원의 워크스페이스로 검색해 근거 규칙과 함께 돌려준다")
    void handle_success_toolsCallSearchDocuments() {
        // given
        Mockito.when(workspaceQueryService.findAllByMemberId(MEMBER_ID))
                .thenReturn(
                        new WorkspaceListResult(
                                null,
                                List.of(
                                        new WorkspaceListItemResult(
                                                11L,
                                                "우테코"
                                        )
                                )
                        )
                );
        Mockito.when(
                workspaceSearchService.search(
                        11L,
                        MEMBER_ID,
                        "왜 PostgreSQL인가요?"
                )
        )
                .thenReturn(
                        WorkspaceSearchResult.fallback(
                                SearchResultStatus.NO_RESULT,
                                "관련된 정보를 찾지 못했습니다."
                        )
                );
        String body = """
                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                 "params":{"name":"search_documents","arguments":{"query":"왜 PostgreSQL인가요?"}}}
                """;

        // when
        McpHttpResponse response = handler.handle(
                body,
                MEMBER_ID
        );

        // then
        Map<String, Object> result = successResult(response);
        assertThat(result).doesNotContainKey("isError");
        assertThat(textOf(result)).isEqualTo("관련된 정보를 찾지 못했습니다.");
    }

    @Test
    @DisplayName("도구 인자가 스키마와 다르면 JSON-RPC INVALID_PARAMS로 답한다")
    void handle_failure_toolsCallInvalidArguments() {
        // given
        String body = """
                {"jsonrpc":"2.0","id":4,"method":"tools/call",
                 "params":{"name":"search_documents","arguments":{"query":"  "}}}
                """;

        // when
        McpHttpResponse response = handler.handle(
                body,
                MEMBER_ID
        );

        // then
        assertThat(response.status()).isEqualTo(200);
        assertThat(failure(response).error()
                .code()).isEqualTo(JsonRpc.INVALID_PARAMS);
        Mockito.verifyNoInteractions(workspaceSearchService);
    }

    @Test
    @DisplayName("모르는 도구 이름은 INVALID_PARAMS, 모르는 메서드는 METHOD_NOT_FOUND로 답한다")
    void handle_failure_unknownToolAndMethod() {
        // given

        // when
        McpHttpResponse unknownTool = handler.handle(
                """
                        {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"show_answer","arguments":{}}}
                        """,
                MEMBER_ID
        );
        McpHttpResponse unknownMethod = handler.handle(
                """
                        {"jsonrpc":"2.0","id":6,"method":"resources/list"}
                        """,
                MEMBER_ID
        );

        // then
        assertThat(failure(unknownTool).error()
                .code()).isEqualTo(JsonRpc.INVALID_PARAMS);
        assertThat(failure(unknownMethod).error()
                .code()).isEqualTo(JsonRpc.METHOD_NOT_FOUND);
    }

    @Test
    @DisplayName("id 없는 알림과 클라이언트 응답은 본문 없이 202로 받는다")
    void handle_success_notificationsAndResponsesAreAccepted() {
        // given

        // when
        McpHttpResponse notification = handler.handle(
                """
                        {"jsonrpc":"2.0","method":"notifications/initialized"}
                        """,
                MEMBER_ID
        );
        McpHttpResponse clientResponse = handler.handle(
                """
                        {"jsonrpc":"2.0","id":9,"result":{}}
                        """,
                MEMBER_ID
        );

        // then
        assertThat(notification.status()).isEqualTo(202);
        assertThat(notification.body()).isNull();
        assertThat(clientResponse.status()).isEqualTo(202);
        assertThat(clientResponse.body()).isNull();
    }

    @Test
    @DisplayName("JSON이 아니면 PARSE_ERROR, 배치·jsonrpc 불일치는 INVALID_REQUEST로 400을 준다")
    void handle_failure_malformedMessages() {
        // given

        // when
        McpHttpResponse parseError = handler.handle(
                "not-json",
                MEMBER_ID
        );
        McpHttpResponse batch = handler.handle(
                """
                        [{"jsonrpc":"2.0","id":1,"method":"ping"}]
                        """,
                MEMBER_ID
        );
        McpHttpResponse wrongVersion = handler.handle(
                """
                        {"jsonrpc":"1.0","id":1,"method":"ping"}
                        """,
                MEMBER_ID
        );
        McpHttpResponse empty = handler.handle(
                null,
                MEMBER_ID
        );

        // then
        assertThat(parseError.status()).isEqualTo(400);
        assertThat(failure(parseError).error()
                .code()).isEqualTo(JsonRpc.PARSE_ERROR);
        assertThat(batch.status()).isEqualTo(400);
        assertThat(failure(batch).error()
                .code()).isEqualTo(JsonRpc.INVALID_REQUEST);
        assertThat(wrongVersion.status()).isEqualTo(400);
        assertThat(failure(wrongVersion).error()
                .code()).isEqualTo(JsonRpc.INVALID_REQUEST);
        assertThat(empty.status()).isEqualTo(400);
        assertThat(failure(empty).error()
                .code()).isEqualTo(JsonRpc.PARSE_ERROR);
    }

    @Test
    @DisplayName("ping은 빈 결과로 답한다")
    void handle_success_ping() {
        // given

        // when
        McpHttpResponse response = handler.handle(
                """
                        {"jsonrpc":"2.0","id":8,"method":"ping"}
                        """,
                MEMBER_ID
        );

        // then
        assertThat(successResult(response)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> successResult(McpHttpResponse response) {
        assertThat(response.body()).isInstanceOf(JsonRpc.Success.class);
        JsonRpc.Success success = (JsonRpc.Success) response.body();
        assertThat(success.jsonrpc()).isEqualTo(JsonRpc.VERSION);
        return (Map<String, Object>) success.result();
    }

    private JsonRpc.Failure failure(McpHttpResponse response) {
        assertThat(response.body()).isInstanceOf(JsonRpc.Failure.class);
        return (JsonRpc.Failure) response.body();
    }

    @SuppressWarnings("unchecked")
    private String textOf(Map<String, Object> callToolResult) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) callToolResult.get("content");
        return (String) content.getFirst()
                .get("text");
    }
}

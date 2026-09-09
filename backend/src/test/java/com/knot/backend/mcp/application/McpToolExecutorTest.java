package com.knot.backend.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.chat.application.WorkspaceSearchService;
import com.knot.backend.chat.application.dto.result.WorkspaceSearchResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchResultStatus;
import com.knot.backend.workspace.application.WorkspaceQueryService;
import com.knot.backend.workspace.application.dto.result.WorkspaceListItemResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceListResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 원격 MCP 서버의 도구 실행(기획서 6.6, 로드맵 A13). 결과 텍스트·오류 모양은 데스크톱 로컬 MCP 서버의 도구 실행과
 * 같아야 스킬 한 벌이 두 서버에 그대로 통한다.
 */
class McpToolExecutorTest {
    private static final long MEMBER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkspaceQueryService workspaceQueryService = Mockito.mock(WorkspaceQueryService.class);
    private final WorkspaceSearchService workspaceSearchService = Mockito.mock(WorkspaceSearchService.class);
    private final McpToolExecutor executor = new McpToolExecutor(
            workspaceQueryService,
            workspaceSearchService
    );

    @Test
    @DisplayName("list_workspaces는 목록 텍스트와 structuredContent를 함께 돌려준다")
    void execute_success_listWorkspaces() {
        // given
        givenWorkspaces(
                new WorkspaceListItemResult(
                        11L,
                        "우테코"
                ),
                new WorkspaceListItemResult(
                        12L,
                        "사이드"
                )
        );

        // when
        McpToolResult result = executor.execute(
                McpToolCatalog.LIST_WORKSPACES,
                null,
                MEMBER_ID
        );

        // then
        assertThat(result.isError()).isFalse();
        assertThat(result.text()).isEqualTo("""
                워크스페이스 2개:
                - [11] 우테코
                - [12] 사이드""");
        assertThat(result.structuredContent()).isEqualTo(
                Map.of(
                        "workspaces",
                        List.of(
                                new McpWorkspaceItem(
                                        11L,
                                        "우테코"
                                ),
                                new McpWorkspaceItem(
                                        12L,
                                        "사이드"
                                )
                        )
                )
        );
    }

    @Test
    @DisplayName("workspaceId를 생략하면 하나뿐인 워크스페이스로 검색한다")
    void execute_success_searchDocumentsWithSingleWorkspace() {
        // given
        givenWorkspaces(
                new WorkspaceListItemResult(
                        11L,
                        "우테코"
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
                        WorkspaceSearchResult.ready(
                                "근거 규칙\n\n",
                                List.of(chunk())
                        )
                );

        // when
        McpToolResult result = executor.execute(
                McpToolCatalog.SEARCH_DOCUMENTS,
                arguments("""
                        {"query":"왜 PostgreSQL인가요?"}
                        """),
                MEMBER_ID
        );

        // then
        assertThat(result.isError()).isFalse();
        assertThat(result.text()).isEqualTo("""
                근거 규칙

                [근거 문서 1]
                제목: DB 기술 선정 회의록
                문서 ID: 201
                문서 링크: https://notion.test/db
                청크: 2
                내용:
                PostgreSQL은 pgvector 확장을 위해 선택했다.

                """);
    }

    @Test
    @DisplayName("workspaceId 없이 워크스페이스가 여럿이면 목록과 함께 isError로 되묻는다")
    void execute_success_searchDocumentsAmbiguousWorkspace() {
        // given
        givenWorkspaces(
                new WorkspaceListItemResult(
                        11L,
                        "우테코"
                ),
                new WorkspaceListItemResult(
                        12L,
                        "사이드"
                )
        );

        // when
        McpToolResult result = executor.execute(
                McpToolCatalog.SEARCH_DOCUMENTS,
                arguments("""
                        {"query":"질문"}
                        """),
                MEMBER_ID
        );

        // then
        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains(
                McpToolExecutor.WORKSPACE_AMBIGUOUS_MESSAGE,
                "- [11] 우테코"
        );
        assertThat(result.logStatus()).isEqualTo("error:" + McpToolExecutor.WORKSPACE_AMBIGUOUS_CODE);
        Mockito.verifyNoInteractions(workspaceSearchService);
    }

    @Test
    @DisplayName("속한 워크스페이스가 없으면 안내 문구를 isError로 돌려준다")
    void execute_success_searchDocumentsWithoutWorkspace() {
        // given
        givenWorkspaces();

        // when
        McpToolResult result = executor.execute(
                McpToolCatalog.SEARCH_DOCUMENTS,
                arguments("""
                        {"query":"질문"}
                        """),
                MEMBER_ID
        );

        // then
        assertThat(result.isError()).isTrue();
        assertThat(result.logStatus()).isEqualTo("error:" + McpToolExecutor.NO_WORKSPACE_CODE);
    }

    @Test
    @DisplayName("READY가 아니면 안내 문구만 돌려주고 근거는 비운다")
    void execute_success_searchDocumentsFallback() {
        // given
        Mockito.when(
                workspaceSearchService.search(
                        11L,
                        MEMBER_ID,
                        "질문"
                )
        )
                .thenReturn(
                        WorkspaceSearchResult.fallback(
                                SearchResultStatus.NEEDS_CLARIFICATION,
                                "범위가 넓어요."
                        )
                );

        // when
        McpToolResult result = executor.execute(
                McpToolCatalog.SEARCH_DOCUMENTS,
                arguments("""
                        {"query":"질문","workspaceId":11}
                        """),
                MEMBER_ID
        );

        // then
        assertThat(result.isError()).isFalse();
        assertThat(result.text()).isEqualTo("범위가 넓어요.");
        assertThat(result.structuredContent()).isEqualTo(
                Map.of(
                        "status",
                        SearchResultStatus.NEEDS_CLARIFICATION,
                        "fallbackAnswer",
                        "범위가 넓어요.",
                        "chunks",
                        List.of()
                )
        );
    }

    @Test
    @DisplayName("검사에 걸린 서비스 예외는 그 코드·문구를 그대로 isError로 중계한다")
    void execute_success_relaysServiceErrorCode() {
        // given
        Mockito.when(
                workspaceSearchService.search(
                        11L,
                        MEMBER_ID,
                        "질문"
                )
        )
                .thenThrow(new ChatException(ChatErrorCode.CHAT_DOCUMENTS_NOT_READY));

        // when
        McpToolResult result = executor.execute(
                McpToolCatalog.SEARCH_DOCUMENTS,
                arguments("""
                        {"query":"질문","workspaceId":11}
                        """),
                MEMBER_ID
        );

        // then
        assertThat(result.isError()).isTrue();
        assertThat(result.text()).startsWith(ChatErrorCode.CHAT_DOCUMENTS_NOT_READY.getCode() + ": ");
        assertThat(result.logStatus()).isEqualTo("error:" + ChatErrorCode.CHAT_DOCUMENTS_NOT_READY.getCode());
    }

    @Test
    @DisplayName("질문이 비었거나 상한을 넘거나 workspaceId가 양의 정수가 아니면 입력 오류다")
    void execute_failure_invalidInput() {
        // given
        String tooLong = "가".repeat(McpToolCatalog.MAX_QUERY_LENGTH + 1);

        // when
        ThrowingCallable blank = () -> executor.execute(
                McpToolCatalog.SEARCH_DOCUMENTS,
                arguments("""
                        {"query":"   "}
                        """),
                MEMBER_ID
        );
        ThrowingCallable missing = () -> executor.execute(
                McpToolCatalog.SEARCH_DOCUMENTS,
                arguments("{}"),
                MEMBER_ID
        );
        ThrowingCallable overLimit = () -> executor.execute(
                McpToolCatalog.SEARCH_DOCUMENTS,
                arguments("{\"query\":\"" + tooLong + "\"}"),
                MEMBER_ID
        );
        ThrowingCallable negativeWorkspace = () -> executor.execute(
                McpToolCatalog.SEARCH_DOCUMENTS,
                arguments("""
                        {"query":"질문","workspaceId":0}
                        """),
                MEMBER_ID
        );
        ThrowingCallable arrayArguments = () -> executor.execute(
                McpToolCatalog.LIST_WORKSPACES,
                arguments("[]"),
                MEMBER_ID
        );

        // then
        assertThatThrownBy(blank).isInstanceOf(McpToolInputException.class);
        assertThatThrownBy(missing).isInstanceOf(McpToolInputException.class);
        assertThatThrownBy(overLimit).isInstanceOf(McpToolInputException.class);
        assertThatThrownBy(negativeWorkspace).isInstanceOf(McpToolInputException.class);
        assertThatThrownBy(arrayArguments).isInstanceOf(McpToolInputException.class);
        Mockito.verifyNoInteractions(workspaceSearchService);
    }

    @Test
    @DisplayName("질문 본문은 입력 오류 메시지에 담기지 않는다")
    void execute_failure_doesNotLeakQuery() {
        // given
        String secret = "비밀 질문 " + "가".repeat(McpToolCatalog.MAX_QUERY_LENGTH);

        // when & then
        assertThatThrownBy(
                () -> executor.execute(
                        McpToolCatalog.SEARCH_DOCUMENTS,
                        arguments("{\"query\":\"" + secret + "\"}"),
                        MEMBER_ID
                )
        )
                .isInstanceOf(McpToolInputException.class)
                .hasMessageNotContaining("비밀 질문");
    }

    private void givenWorkspaces(WorkspaceListItemResult... workspaces) {
        Mockito.when(workspaceQueryService.findAllByMemberId(MEMBER_ID))
                .thenReturn(
                        new WorkspaceListResult(
                                null,
                                List.of(workspaces)
                        )
                );
    }

    private JsonNode arguments(String json) {
        return objectMapper.readTree(json);
    }

    private static SearchChunk chunk() {
        return SearchChunk.retrieved(
                1L,
                201L,
                301L,
                2,
                "DB 기술 선정 회의록",
                "https://notion.test/db",
                Instant.parse("2026-09-01T00:00:00Z"),
                "PostgreSQL은 pgvector 확장을 위해 선택했다.",
                0.95
        );
    }
}

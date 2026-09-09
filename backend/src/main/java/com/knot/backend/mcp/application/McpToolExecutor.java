package com.knot.backend.mcp.application;

import com.knot.backend.chat.application.WorkspaceSearchService;
import com.knot.backend.chat.application.dto.result.WorkspaceSearchResult;
import com.knot.backend.global.exception.ProjectException;
import com.knot.backend.workspace.application.WorkspaceQueryService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 원격 MCP 서버의 도구 실행(기획서 6.5, 로드맵 A13). 데스크톱 로컬 MCP 서버의 main 쪽 도구 실행
 * ({@code desktop/src/main/agent/toolExecutor.ts})과 같은 규칙으로 결과를 조립한다 — 다른 점은 HTTP를 거치지 않고
 * 같은 서비스를 직접 부른다는 것뿐이다.
 *
 * <ul>
 * <li>{@code list_workspaces()} → 텍스트 목록 + {@code structuredContent.workspaces}</li>
 * <li>{@code search_documents({query, workspaceId?})} → {@code groundingRules} + {@code [근거 문서 n]} 블록(청크 순번
 * 포함) + {@code structuredContent {status, chunks}}. {@code workspaceId}가 없고 워크스페이스가 하나면 그것, 여럿이면
 * {@code isError}로 목록을 돌려주고 {@code list_workspaces}를 안내한다. READY가 아니면 {@code fallbackAnswer}만</li>
 * <li>서비스 오류는 {@code 코드: 문구} 텍스트의 {@code isError}로. 인자가 스키마와 다르면 {@link McpToolInputException}</li>
 * </ul>
 *
 * 로그에는 도구 이름·workspaceId·상태·지연 ms만 남기고 질문·본문은 남기지 않는다(로드맵 Q49).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolExecutor {
    static final String NO_WORKSPACE_CODE = "NO_WORKSPACE";
    static final String NO_WORKSPACE_MESSAGE = "속한 워크스페이스가 없어요. Knot 앱에서 워크스페이스를 만들거나 초대를 받으세요";
    static final String WORKSPACE_AMBIGUOUS_CODE = "WORKSPACE_AMBIGUOUS";
    static final String WORKSPACE_AMBIGUOUS_MESSAGE = "워크스페이스가 여러 개예요. workspaceId를 지정해 다시 검색하세요(목록은 list_workspaces).";
    static final String UNKNOWN_CODE = "UNKNOWN";
    static final String UNKNOWN_MESSAGE = "도구 실행에 실패했어요";

    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceSearchService workspaceSearchService;

    public McpToolResult execute(
            String tool,
            JsonNode arguments,
            long memberId
    ) {
        long startedAt = System.nanoTime();
        Execution execution = null;
        try {
            execution = switch (tool) {
                case McpToolCatalog.LIST_WORKSPACES -> listWorkspaces(
                        arguments,
                        memberId
                );
                case McpToolCatalog.SEARCH_DOCUMENTS -> searchDocuments(
                        arguments,
                        memberId
                );
                default -> throw new McpToolInputException("Unknown tool: " + tool);
            };
            return execution.result();
        } catch (McpToolInputException exception) {
            execution = new Execution(
                    McpToolResult.error(
                            "INVALID_TOOL_INPUT",
                            exception.getMessage()
                    ),
                    null
            );
            throw exception;
        } catch (ProjectException exception) {
            execution = new Execution(
                    McpToolResult.error(
                            exception.getErrorCode()
                                    .getCode(),
                            exception.getErrorCode()
                                    .getMessage()
                    ),
                    null
            );
            return execution.result();
        } catch (RuntimeException exception) {
            log.warn(
                    "MCP 도구 실행 실패 tool={}",
                    tool,
                    exception
            );
            execution = new Execution(
                    McpToolResult.error(
                            UNKNOWN_CODE,
                            UNKNOWN_MESSAGE
                    ),
                    null
            );
            return execution.result();
        } finally {
            log.info(
                    "MCP 도구 호출 tool={} workspaceId={} status={} latencyMs={}",
                    tool,
                    execution == null ? null : execution.workspaceId(),
                    execution == null ? null : execution.result()
                            .logStatus(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            );
        }
    }

    private Execution listWorkspaces(
            JsonNode arguments,
            long memberId
    ) {
        requireObjectOrMissing(arguments);
        List<McpWorkspaceItem> workspaces = findWorkspaces(memberId);
        return new Execution(
                McpToolResult.ok(
                        formatWorkspaceList(workspaces),
                        Map.of(
                                "workspaces",
                                workspaces
                        )
                ),
                null
        );
    }

    private Execution searchDocuments(
            JsonNode arguments,
            long memberId
    ) {
        SearchInput input = parseSearchInput(arguments);
        Long workspaceId = input.workspaceId();
        if (workspaceId == null) {
            List<McpWorkspaceItem> workspaces = findWorkspaces(memberId);
            if (workspaces.isEmpty()) {
                return new Execution(
                        McpToolResult.error(
                                NO_WORKSPACE_CODE,
                                NO_WORKSPACE_MESSAGE
                        ),
                        null
                );
            }
            if (workspaces.size() > 1) {
                return new Execution(
                        McpToolResult.error(
                                WORKSPACE_AMBIGUOUS_CODE,
                                WORKSPACE_AMBIGUOUS_MESSAGE + "\n" + formatWorkspaceList(workspaces),
                                Map.of(
                                        "workspaces",
                                        workspaces
                                )
                        ),
                        null
                );
            }
            workspaceId = workspaces.getFirst()
                    .id();
        }
        WorkspaceSearchResult result = workspaceSearchService.search(
                workspaceId,
                memberId,
                input.query()
        );
        return new Execution(
                formatSearchResult(result),
                workspaceId
        );
    }

    private List<McpWorkspaceItem> findWorkspaces(long memberId) {
        return workspaceQueryService.findAllByMemberId(memberId)
                .workspaces()
                .stream()
                .map(McpWorkspaceItem::from)
                .toList();
    }

    static String formatWorkspaceList(List<McpWorkspaceItem> workspaces) {
        if (workspaces.isEmpty()) {
            return NO_WORKSPACE_MESSAGE;
        }
        StringBuilder text = new StringBuilder("워크스페이스 " + workspaces.size() + "개:");
        for (McpWorkspaceItem workspace : workspaces) {
            text.append("\n- [")
                    .append(workspace.id())
                    .append("] ")
                    .append(workspace.name());
        }
        return text.toString();
    }

    /** 현행 {@code SearchContext.groundingBlock}과 같은 형식에 청크 순번 줄을 더한다(로드맵 Q49). */
    static String groundingBlock(
            int index,
            McpSearchChunk chunk
    ) {
        return "[근거 문서 " + (index + 1) + "]\n제목: " + chunk.title() + "\n문서 ID: " + chunk.importedPageId() + "\n문서 링크: "
                + chunk.sourceUrl() + "\n청크: " + chunk.chunkIndex() + "\n내용:\n" + chunk.content() + "\n\n";
    }

    static McpToolResult formatSearchResult(WorkspaceSearchResult result) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put(
                "status",
                result.status()
        );
        if (!result.isReady()) {
            structuredContent.put(
                    "fallbackAnswer",
                    result.fallbackAnswer()
            );
            structuredContent.put(
                    "chunks",
                    List.of()
            );
            return McpToolResult.ok(
                    result.fallbackAnswer(),
                    structuredContent
            );
        }
        List<McpSearchChunk> chunks = result.chunks()
                .stream()
                .map(McpSearchChunk::from)
                .toList();
        StringBuilder text = new StringBuilder(result.groundingRules());
        for (int index = 0; index < chunks.size(); index++) {
            text.append(
                    groundingBlock(
                            index,
                            chunks.get(index)
                    )
            );
        }
        structuredContent.put(
                "chunks",
                chunks
        );
        return McpToolResult.ok(
                text.toString(),
                structuredContent
        );
    }

    /** 입력 스키마와 같은 검사. 질문 본문은 오류 메시지에 넣지 않는다. */
    static SearchInput parseSearchInput(JsonNode arguments) {
        requireObjectOrMissing(arguments);
        JsonNode query = arguments == null ? null : arguments.get("query");
        if (query == null || !query.isString() || query.asString()
                .isBlank() || query.asString()
                        .length() > McpToolCatalog.MAX_QUERY_LENGTH) {
            throw new McpToolInputException("query는 1~" + McpToolCatalog.MAX_QUERY_LENGTH + "자여야 한다.");
        }
        JsonNode workspaceId = arguments.get("workspaceId");
        if (workspaceId == null || workspaceId.isNull()) {
            return new SearchInput(
                    query.asString(),
                    null
            );
        }
        if (!workspaceId.isIntegralNumber() || workspaceId.longValue() <= 0) {
            throw new McpToolInputException("workspaceId는 양의 정수여야 한다.");
        }
        return new SearchInput(
                query.asString(),
                workspaceId.longValue()
        );
    }

    private static void requireObjectOrMissing(JsonNode arguments) {
        if (arguments != null && !arguments.isNull() && !arguments.isObject()) {
            throw new McpToolInputException("arguments는 객체여야 한다.");
        }
    }

    record SearchInput(
            String query,
            Long workspaceId
    ) {
    }

    private record Execution(
            McpToolResult result,
            Long workspaceId
    ) {
    }
}

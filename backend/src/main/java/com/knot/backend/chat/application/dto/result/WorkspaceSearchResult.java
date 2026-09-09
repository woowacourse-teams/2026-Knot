package com.knot.backend.chat.application.dto.result;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchResultStatus;
import java.util.List;

/**
 * Workspace 검색 API 결과(기획서 6.4, 로드맵 S7). READY면 groundingRules·chunks, 아니면
 * fallbackAnswer만 채워진다. 세션 검색(S1)과 달리 아무것도 저장하지 않으므로 메시지 ID가 없다.
 */
public record WorkspaceSearchResult(
        SearchResultStatus status,
        String groundingRules,
        List<SearchChunk> chunks,
        String fallbackAnswer
) {

    public WorkspaceSearchResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public static WorkspaceSearchResult ready(
            String groundingRules,
            List<SearchChunk> chunks
    ) {
        return new WorkspaceSearchResult(
                SearchResultStatus.READY,
                groundingRules,
                chunks,
                null
        );
    }

    public static WorkspaceSearchResult fallback(
            SearchResultStatus status,
            String fallbackAnswer
    ) {
        return new WorkspaceSearchResult(
                status,
                null,
                List.of(),
                fallbackAnswer
        );
    }

    public boolean isReady() {
        return status == SearchResultStatus.READY;
    }
}

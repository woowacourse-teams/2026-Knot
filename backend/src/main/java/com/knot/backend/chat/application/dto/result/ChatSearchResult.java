package com.knot.backend.chat.application.dto.result;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchResultStatus;
import java.util.List;

/**
 * 검색 API 결과(기획서 6.4). READY면 groundingRules·chunks가 채워지고, 아니면 서버가 저장한 안내 답변의
 * assistantMessageId·fallbackAnswer가 채워진다.
 */
public record ChatSearchResult(
        SearchResultStatus status,
        long userMessageId,
        String groundingRules,
        List<SearchChunk> chunks,
        Long assistantMessageId,
        String fallbackAnswer
) {

    public ChatSearchResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public static ChatSearchResult ready(
            long userMessageId,
            String groundingRules,
            List<SearchChunk> chunks
    ) {
        return new ChatSearchResult(
                SearchResultStatus.READY,
                userMessageId,
                groundingRules,
                chunks,
                null,
                null
        );
    }

    public static ChatSearchResult fallback(
            SearchResultStatus status,
            long userMessageId,
            long assistantMessageId,
            String fallbackAnswer
    ) {
        return new ChatSearchResult(
                status,
                userMessageId,
                null,
                List.of(),
                assistantMessageId,
                fallbackAnswer
        );
    }

    public boolean isReady() {
        return status == SearchResultStatus.READY;
    }
}

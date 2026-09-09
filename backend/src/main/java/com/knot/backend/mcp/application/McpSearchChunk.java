package com.knot.backend.mcp.application;

import com.knot.backend.search.domain.SearchChunk;

/** {@code search_documents}의 {@code structuredContent.chunks} 항목. Workspace 검색 API 응답의 청크와 같은 필드다. */
public record McpSearchChunk(
        long importRunId,
        long importedPageId,
        int chunkIndex,
        String title,
        String sourceUrl,
        String content,
        double score
) {

    public static McpSearchChunk from(SearchChunk chunk) {
        return new McpSearchChunk(
                chunk.importRunId(),
                chunk.importedPageId(),
                chunk.chunkIndex(),
                chunk.title(),
                chunk.sourceUrl(),
                chunk.content(),
                chunk.score()
        );
    }
}

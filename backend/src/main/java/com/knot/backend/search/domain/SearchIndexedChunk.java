package com.knot.backend.search.domain;

import java.util.Arrays;

public final class SearchIndexedChunk {
    private final Long importedPageId;
    private final Long importRunId;
    private final int chunkIndex;
    private final String content;
    private final double[] embedding;

    private SearchIndexedChunk(
            Long importedPageId,
            Long importRunId,
            int chunkIndex,
            String content,
            double[] embedding
    ) {
        this.importedPageId = importedPageId;
        this.importRunId = importRunId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = Arrays.copyOf(
                embedding,
                embedding.length
        );
    }

    public static SearchIndexedChunk of(
            Long importedPageId,
            Long importRunId,
            int chunkIndex,
            String content,
            double[] embedding
    ) {
        if (importedPageId == null || importedPageId <= 0 || importRunId == null || importRunId <= 0 || chunkIndex < 0
                || content == null || content.isBlank() || embedding == null || embedding.length == 0) {
            throw new SearchException(SearchErrorCode.SEARCH_INDEX_FAILED);
        }
        return new SearchIndexedChunk(
                importedPageId,
                importRunId,
                chunkIndex,
                content,
                embedding
        );
    }

    public Long importedPageId() {
        return importedPageId;
    }

    public Long importRunId() {
        return importRunId;
    }

    public int chunkIndex() {
        return chunkIndex;
    }

    public String content() {
        return content;
    }

    public double[] embedding() {
        return Arrays.copyOf(
                embedding,
                embedding.length
        );
    }
}

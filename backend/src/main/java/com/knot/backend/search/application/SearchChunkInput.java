package com.knot.backend.search.application;

public final class SearchChunkInput {
    private final Long pageId;
    private final int chunkIndex;
    private final String content;
    private final String embeddingText;

    public SearchChunkInput(
            Long pageId,
            int chunkIndex,
            String content,
            String embeddingText
    ) {
        this.pageId = pageId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embeddingText = embeddingText;
    }

    public Long pageId() {
        return pageId;
    }

    public int chunkIndex() {
        return chunkIndex;
    }

    public String content() {
        return content;
    }

    public String embeddingText() {
        return embeddingText;
    }
}

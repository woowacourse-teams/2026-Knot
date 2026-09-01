package com.knot.backend.search.domain;

import java.time.Instant;

public final class SearchChunk {
    private final Long importedPageId;
    private final Long workspaceId;
    private final Long importRunId;
    private final int chunkIndex;
    private final String title;
    private final String sourceUrl;
    private final Instant createdAt;
    private final String content;
    private final double score;

    private SearchChunk(
            Long importedPageId,
            Long workspaceId,
            Long importRunId,
            int chunkIndex,
            String title,
            String sourceUrl,
            Instant createdAt,
            String content,
            double score
    ) {
        this.importedPageId = importedPageId;
        this.workspaceId = workspaceId;
        this.importRunId = importRunId;
        this.chunkIndex = chunkIndex;
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.createdAt = createdAt;
        this.content = content;
        this.score = score;
    }

    public static SearchChunk retrieved(
            Long workspaceId,
            Long importedPageId,
            Long importRunId,
            int chunkIndex,
            String title,
            String sourceUrl,
            Instant createdAt,
            String content,
            double score
    ) {
        return new SearchChunk(
                importedPageId,
                workspaceId,
                importRunId,
                chunkIndex,
                title,
                sourceUrl,
                createdAt,
                content,
                score
        );
    }

    public SearchChunk withScore(double score) {
        return retrieved(
                workspaceId,
                importedPageId,
                importRunId,
                chunkIndex,
                title,
                sourceUrl,
                createdAt,
                content,
                score
        );
    }

    public Long importedPageId() {
        return importedPageId;
    }

    public Long workspaceId() {
        return workspaceId;
    }

    public Long importRunId() {
        return importRunId;
    }

    public int chunkIndex() {
        return chunkIndex;
    }

    public String title() {
        return title;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String content() {
        return content;
    }

    public double score() {
        return score;
    }
}

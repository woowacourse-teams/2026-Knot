package com.knot.backend.search.domain;

import java.util.List;
import java.util.Optional;

public interface SearchChunkRepository {

    Optional<Long> findPublishedImportRunId(Long workspaceId);

    void replace(
            Long workspaceId,
            Long importRunId,
            List<SearchIndexedChunk> chunks
    );

    List<SearchChunk> findByVector(
            Long workspaceId,
            Long importRunId,
            double[] embedding,
            int limit
    );

    List<SearchChunk> findByKeywords(
            Long workspaceId,
            Long importRunId,
            List<String> terms,
            int limit
    );
}

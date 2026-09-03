package com.knot.backend.search.domain;

import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.time.Instant;

public record SearchReference(
        Long id,
        Long messageId,
        int rank,
        double relevanceScore,
        ContentSourceProvider source,
        ContentPageReference page
) {

    public record ContentPageReference(
            String externalPageId,
            String title,
            String sourceUrl,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

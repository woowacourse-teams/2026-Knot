package com.knot.backend.search.domain;

import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.time.Instant;

public record SearchReference(
        Long id,
        Long messageId,
        int rank,
        double relevanceScore,
        ContentSourceProvider source,
        NotionPageReference notionPage
) {

    public record NotionPageReference(
            String id,
            String title,
            String notionUrl,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

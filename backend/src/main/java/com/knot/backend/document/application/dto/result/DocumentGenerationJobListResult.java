package com.knot.backend.document.application.dto.result;

import java.util.List;

public record DocumentGenerationJobListResult(
        List<DocumentGenerationJobItemResult> items,
        String nextCursor
) {
    public DocumentGenerationJobListResult {
        items = List.copyOf(items);
    }
}

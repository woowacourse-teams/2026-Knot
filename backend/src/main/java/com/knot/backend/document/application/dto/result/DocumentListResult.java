package com.knot.backend.document.application.dto.result;

import java.util.List;

public record DocumentListResult(
        List<DocumentTopicResult> topics,
        List<DocumentCardResult> items,
        String nextCursor
) {
    public DocumentListResult {
        topics = List.copyOf(topics);
        items = List.copyOf(items);
    }
}

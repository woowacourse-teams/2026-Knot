package com.knot.backend.search.application.dto.result;

import java.util.List;

public record SearchMessageListResult(
        long conversationId,
        List<SearchMessageListItemResult> items,
        boolean hasPrevious,
        String previousCursor
) {

    public SearchMessageListResult {
        items = List.copyOf(items);
    }
}

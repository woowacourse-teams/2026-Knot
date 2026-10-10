package com.knot.backend.search.application.dto.result;

import java.util.List;

public record SearchConversationListResult(
        List<SearchConversationListItemResult> items,
        String nextCursor
) {

    public SearchConversationListResult {
        items = List.copyOf(items);
    }
}

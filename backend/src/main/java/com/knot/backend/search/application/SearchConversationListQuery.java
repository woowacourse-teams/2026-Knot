package com.knot.backend.search.application;

import com.knot.backend.search.application.dto.result.SearchConversationListItemResult;
import com.knot.backend.search.domain.SearchConversationCursor;
import java.util.List;

public interface SearchConversationListQuery {

    List<SearchConversationListItemResult> findPage(
            long workspaceId,
            long memberId,
            int size,
            SearchConversationCursor cursor
    );
}

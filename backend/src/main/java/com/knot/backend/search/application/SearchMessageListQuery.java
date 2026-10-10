package com.knot.backend.search.application;

import com.knot.backend.search.application.dto.result.SearchEvidenceItemResult;
import com.knot.backend.search.domain.SearchConversation;
import com.knot.backend.search.domain.SearchMessage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SearchMessageListQuery {

    Optional<SearchConversation> findConversation(long conversationId);

    List<SearchMessage> findPage(
            long workspaceId,
            long memberId,
            long conversationId,
            Integer beforeSequence,
            int size
    );

    Map<Long, List<SearchEvidenceItemResult>> findEvidences(
            long workspaceId,
            long memberId,
            long conversationId,
            List<Long> answerIds
    );
}

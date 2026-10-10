package com.knot.backend.search.infrastructure;

import com.knot.backend.search.application.SearchMessageListQuery;
import com.knot.backend.search.domain.SearchConversation;
import com.knot.backend.search.domain.SearchMessage;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SearchMessageListQueryAdapter implements SearchMessageListQuery {

    private final SearchMessageListJpaRepository messages;

    @Override
    public Optional<SearchConversation> findConversation(long conversationId) {
        return messages.findConversation(conversationId);
    }

    @Override
    public List<SearchMessage> findPage(
            long workspaceId,
            long memberId,
            long conversationId,
            Integer beforeSequence,
            int size
    ) {
        return messages.findPage(
                workspaceId,
                memberId,
                conversationId,
                beforeSequence,
                PageRequest.of(
                        0,
                        size + 1
                )
        );
    }
}

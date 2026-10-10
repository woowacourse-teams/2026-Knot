package com.knot.backend.search.infrastructure;

import com.knot.backend.search.application.SearchMessageListQuery;
import com.knot.backend.search.domain.SearchConversation;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SearchMessageListQueryAdapter implements SearchMessageListQuery {

    private final SearchMessageListJpaRepository messages;

    @Override
    public Optional<SearchConversation> findConversation(long conversationId) {
        return messages.findConversation(conversationId);
    }
}

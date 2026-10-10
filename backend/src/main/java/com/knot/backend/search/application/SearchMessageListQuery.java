package com.knot.backend.search.application;

import com.knot.backend.search.domain.SearchConversation;
import java.util.Optional;

public interface SearchMessageListQuery {

    Optional<SearchConversation> findConversation(long conversationId);
}

package com.knot.backend.search.infrastructure;

import com.knot.backend.search.domain.SearchConversation;
import com.knot.backend.search.domain.SearchMessage;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface SearchMessageListJpaRepository extends Repository<SearchMessage, Long> {

    @Query("select c from SearchConversation c where c.id = :conversationId")
    Optional<SearchConversation> findConversation(long conversationId);
}

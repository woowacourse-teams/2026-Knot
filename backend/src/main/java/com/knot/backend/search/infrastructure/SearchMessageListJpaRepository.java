package com.knot.backend.search.infrastructure;

import com.knot.backend.search.domain.SearchConversation;
import com.knot.backend.search.domain.SearchMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface SearchMessageListJpaRepository extends Repository<SearchMessage, Long> {

    @Query("select c from SearchConversation c where c.id = :conversationId")
    Optional<SearchConversation> findConversation(long conversationId);

    @Query("""
            select m from SearchMessage m
            join SearchConversation c on c.id = m.conversationId
            where c.workspaceId = :workspaceId and c.memberId = :memberId and c.id = :conversationId
                and (:beforeSequence is null or m.sequence < :beforeSequence)
            order by m.sequence desc
            """)
    List<SearchMessage> findPage(
            long workspaceId,
            long memberId,
            long conversationId,
            Integer beforeSequence,
            Pageable pageable
    );
}

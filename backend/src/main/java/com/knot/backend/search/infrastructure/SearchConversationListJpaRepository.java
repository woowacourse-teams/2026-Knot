package com.knot.backend.search.infrastructure;

import com.knot.backend.search.domain.SearchConversation;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface SearchConversationListJpaRepository extends Repository<SearchConversation, Long> {

    @Query("""
            select new com.knot.backend.search.infrastructure.SearchConversationListRow(
                c.id, first.content, latest.content, c.createdAt, c.updatedAt
            )
            from SearchConversation c
            join SearchMessage first
                on first.conversationId = c.id and first.sequence = 1 and first.role = 'USER'
            left join SearchMessage latest
                on latest.conversationId = c.id and latest.sequence = (
                    select max(m.sequence) from SearchMessage m where m.conversationId = c.id
                )
            where c.workspaceId = :workspaceId and c.memberId = :memberId and c.visibleInList = true
                and (:afterCursor = false or c.updatedAt < :cursorTime
                    or (c.updatedAt = :cursorTime and c.id < :cursorId))
            order by c.updatedAt desc, c.id desc
            """)
    List<SearchConversationListRow> findPage(
            long workspaceId,
            long memberId,
            boolean afterCursor,
            Instant cursorTime,
            long cursorId,
            Pageable pageable
    );
}

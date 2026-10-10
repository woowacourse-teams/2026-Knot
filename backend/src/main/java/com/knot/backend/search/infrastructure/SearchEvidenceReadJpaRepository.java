package com.knot.backend.search.infrastructure;

import com.knot.backend.search.domain.SearchEvidence;
import com.knot.backend.search.domain.SearchEvidenceId;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface SearchEvidenceReadJpaRepository extends Repository<SearchEvidence, SearchEvidenceId> {

    @Query("""
            select new com.knot.backend.search.infrastructure.SearchEvidenceRow(
                e.id.messageId, d.id, d.title, d.topic, e.rank
            )
            from SearchEvidence e
            join SearchMessage m on m.id = e.id.messageId
            join SearchConversation c on c.id = m.conversationId
            join Document d on d.id = e.id.documentId and d.workspaceId = c.workspaceId
            where c.workspaceId = :workspaceId and c.memberId = :memberId and c.id = :conversationId
                and m.role = 'ASSISTANT' and m.id in :answerIds
            order by m.id, e.rank
            """)
    List<SearchEvidenceRow> findForMessages(
            long workspaceId,
            long memberId,
            long conversationId,
            List<Long> answerIds
    );
}

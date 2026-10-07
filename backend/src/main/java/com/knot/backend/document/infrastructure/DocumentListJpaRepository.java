package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.Document;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface DocumentListJpaRepository extends Repository<Document, Long> {

    @Query("""
            select new com.knot.backend.document.infrastructure.DocumentTopicRow(d.topic, count(d.id))
            from Document d
            left join DocumentConfirmation mine
                on mine.id.documentId = d.id and mine.id.memberId = :memberId
            where d.workspaceId = :workspaceId
                and (:recordingSessionId is null or d.recordingSessionId = :recordingSessionId)
                and (:confirmationState = 'ALL'
                    or (:confirmationState = 'PENDING' and mine.id.documentId is not null and mine.confirmedAt is null)
                    or (:confirmationState = 'CONFIRMED' and mine.confirmedAt is not null)
                    or (:confirmationState = 'NOT_REQUIRED' and mine.id.documentId is null))
            group by d.topic
            order by d.topic
            """)
    List<DocumentTopicRow> findTopics(
            long workspaceId,
            long memberId,
            Long recordingSessionId,
            String confirmationState
    );
}

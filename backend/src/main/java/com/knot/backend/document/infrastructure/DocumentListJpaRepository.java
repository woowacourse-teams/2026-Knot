package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.Document;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select new com.knot.backend.document.infrastructure.DocumentCardRow(
                d.id, d.recordingSessionId, d.topic, d.title, d.summary, d.status, d.createdAt,
                rs.accumulatedRecordingMillis,
                case when mine.id.documentId is not null then true else false end, mine.confirmedAt
            )
            from Document d
            join RecordingSession rs on rs.id = d.recordingSessionId
            left join DocumentConfirmation mine
                on mine.id.documentId = d.id and mine.id.memberId = :memberId
            where d.workspaceId = :workspaceId
                and (:recordingSessionId is null or d.recordingSessionId = :recordingSessionId)
                and (:confirmationState = 'ALL'
                    or (:confirmationState = 'PENDING' and mine.id.documentId is not null and mine.confirmedAt is null)
                    or (:confirmationState = 'CONFIRMED' and mine.confirmedAt is not null)
                    or (:confirmationState = 'NOT_REQUIRED' and mine.id.documentId is null))
                and (:afterCursor = false or d.createdAt < :cursorTime
                    or (d.createdAt = :cursorTime and d.id < :cursorId))
            order by d.createdAt desc, d.id desc
            """)
    List<DocumentCardRow> findPage(
            long workspaceId,
            long memberId,
            Long recordingSessionId,
            String confirmationState,
            boolean afterCursor,
            Instant cursorTime,
            long cursorId,
            Pageable pageable
    );

    @Query("""
            select new com.knot.backend.document.infrastructure.DocumentCardCountsRow(
                d.id,
                sum(case when c.confirmedAt is not null then 1 else 0 end),
                sum(case when c.id.documentId is not null and c.confirmedAt is null
                    and active.id is not null then 1 else 0 end),
                sum(case when c.id.documentId is not null and c.confirmedAt is null
                    and active.id is null then 1 else 0 end)
            )
            from Document d
            left join DocumentConfirmation c on c.id.documentId = d.id
            left join WorkspaceMember active
                on active.workspaceId = d.workspaceId and active.memberId = c.id.memberId
                    and active.leftAt is null
            where d.workspaceId = :workspaceId and d.id in :documentIds
            group by d.id
            """)
    List<DocumentCardCountsRow> findCounts(
            long workspaceId,
            List<Long> documentIds
    );
}

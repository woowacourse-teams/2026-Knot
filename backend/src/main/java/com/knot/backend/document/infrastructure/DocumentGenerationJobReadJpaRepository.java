package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface DocumentGenerationJobReadJpaRepository extends Repository<DocumentGenerationJob, Long> {

    @Query("""
            select new com.knot.backend.document.infrastructure.DocumentGenerationJobRow(
                j.id, rs.id, rs.title, j.status, j.createdAt, j.updatedAt
            )
            from DocumentGenerationJob j
            join Transcript t on t.id = j.transcriptId
            join RecordingSession rs on rs.id = t.recordingSessionId
            where rs.workspaceId = :workspaceId
                and (j.status in :activeStatuses or (j.status = :failedStatus and j.expiresAt > :now))
                and (:hasCursor = false or j.createdAt < :cursorCreatedAt
                    or (j.createdAt = :cursorCreatedAt and j.id < :cursorJobId))
            order by j.createdAt desc, j.id desc
            """)
    List<DocumentGenerationJobRow> findPage(
            long workspaceId,
            List<DocumentGenerationJobStatus> activeStatuses,
            DocumentGenerationJobStatus failedStatus,
            Instant now,
            boolean hasCursor,
            Instant cursorCreatedAt,
            long cursorJobId,
            Pageable pageable
    );
}

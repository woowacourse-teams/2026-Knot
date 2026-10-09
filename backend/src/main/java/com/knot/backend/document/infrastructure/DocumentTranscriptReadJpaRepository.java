package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.Document;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface DocumentTranscriptReadJpaRepository extends Repository<Document, Long> {

    @Query("""
            select new com.knot.backend.document.infrastructure.DocumentTranscriptSourceRow(
                t.id, r.accumulatedRecordingMillis, t.content
            )
            from Document d
            join Transcript t on t.id = d.sourceTranscriptId
            join RecordingSession r on r.id = d.recordingSessionId
            where d.workspaceId = :workspaceId and d.id = :documentId
            """)
    Optional<DocumentTranscriptSourceRow> findSource(
            long workspaceId,
            long documentId
    );
}

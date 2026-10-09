package com.knot.backend.document.infrastructure;

import com.knot.backend.recording.domain.Transcript;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface DocumentGenerationInputJpaRepository extends Repository<Transcript, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM Transcript t WHERE t.id = :transcriptId AND EXISTS (
                SELECT rs.id FROM RecordingSession rs
                WHERE rs.id = t.recordingSessionId AND rs.workspaceId = :workspaceId
            )
            """)
    Optional<Transcript> findByWorkspaceIdAndId(
            long workspaceId,
            long transcriptId
    );
}

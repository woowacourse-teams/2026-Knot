package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationJob;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface DocumentGenerationJobJpaRepository extends JpaRepository<DocumentGenerationJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT j FROM DocumentGenerationJob j
            WHERE j.id = :jobId AND EXISTS (
                SELECT t.id FROM Transcript t JOIN RecordingSession rs ON rs.id = t.recordingSessionId
                WHERE t.id = j.transcriptId AND rs.workspaceId = :workspaceId
            )
            """)
    Optional<DocumentGenerationJob> findByWorkspaceIdAndId(
            long workspaceId,
            long jobId
    );
}

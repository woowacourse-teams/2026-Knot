package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentRetentionCandidate;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

interface DocumentGenerationBatchJpaRepository extends JpaRepository<DocumentGenerationBatch, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentGenerationBatch> findByRecordingSessionId(long recordingSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM DocumentGenerationBatch b WHERE b.id = :batchId")
    Optional<DocumentGenerationBatch> findLockedById(long batchId);

    @Query("""
            SELECT b FROM DocumentGenerationBatch b
            WHERE b.recordingSessionId = :recordingSessionId AND EXISTS (
                SELECT r.id FROM RecordingSession r WHERE r.id = b.recordingSessionId AND r.workspaceId = :workspaceId
            )
            """)
    Optional<DocumentGenerationBatch> findByWorkspaceIdAndRecordingSessionId(
            long workspaceId,
            long recordingSessionId
    );

    boolean existsByIdAndRecordingSessionId(
            long batchId,
            long recordingSessionId
    );

    @Transactional(readOnly = true)
    @Query("""
            SELECT new com.knot.backend.document.domain.DocumentRetentionCandidate(r.workspaceId, r.id, b.id)
            FROM DocumentGenerationBatch b JOIN RecordingSession r ON r.id = b.recordingSessionId
            WHERE (b.transcriptId IS NOT NULL AND b.processingStatus =
                com.knot.backend.document.domain.DocumentGenerationProcessingStatus.NO_CONTENT)
                OR EXISTS (
                    SELECT j.id FROM DocumentGenerationJob j WHERE j.batchId = b.id
                        AND j.status = com.knot.backend.document.domain.DocumentGenerationJobStatus.FAILED
                        AND j.expiresAt <= :now AND NOT EXISTS (
                            SELECT d.id FROM Document d WHERE d.documentGenerationJobId = j.id
                        )
                )
            ORDER BY b.id
            """)
    List<DocumentRetentionCandidate> findRetentionCandidates(
            Instant now,
            Pageable pageable
    );
}

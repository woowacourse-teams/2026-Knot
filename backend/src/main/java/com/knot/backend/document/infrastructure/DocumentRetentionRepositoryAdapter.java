package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentRetentionCandidate;
import com.knot.backend.document.domain.DocumentRetentionRepository;
import com.knot.backend.recording.domain.Transcript;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class DocumentRetentionRepositoryAdapter implements DocumentRetentionRepository {

    private final EntityManager entityManager;

    @Override
    public boolean belongsToRecording(
            long batchId,
            long recordingId
    ) {
        return entityManager.createQuery(
                "SELECT count(b) FROM DocumentGenerationBatch b WHERE b.id = :batch AND b.recordingSessionId = :recording",
                Long.class
        )
                .setParameter(
                        "batch",
                        batchId
                )
                .setParameter(
                        "recording",
                        recordingId
                )
                .getSingleResult() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentRetentionCandidate> findCandidates(
            Instant now,
            int limit
    ) {
        return entityManager.createQuery(
                """
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
                        """,
                DocumentRetentionCandidate.class
        )
                .setParameter(
                        "now",
                        now
                )
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<DocumentGenerationJob> findJobsForUpdate(long batchId) {
        return entityManager.createQuery(
                """
                        SELECT j FROM DocumentGenerationJob j WHERE j.batchId = :batchId ORDER BY j.id
                        """,
                DocumentGenerationJob.class
        )
                .setParameter(
                        "batchId",
                        batchId
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    @Override
    public Optional<Transcript> findInputForUpdate(long batchId) {
        return entityManager.createQuery(
                """
                        SELECT t FROM Transcript t WHERE EXISTS (
                            SELECT b.id FROM DocumentGenerationBatch b WHERE b.id = :batchId AND b.transcriptId = t.id
                        )
                        """,
                Transcript.class
        )
                .setParameter(
                        "batchId",
                        batchId
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }

    @Override
    public boolean hasDocumentForInput(long transcriptId) {
        return entityManager.createQuery(
                "SELECT count(d) FROM Document d WHERE d.sourceTranscriptId = :id",
                Long.class
        )
                .setParameter(
                        "id",
                        transcriptId
                )
                .getSingleResult() > 0;
    }

    @Override
    public boolean hasDocumentForJob(long jobId) {
        return entityManager.createQuery(
                "SELECT count(d) FROM Document d WHERE d.documentGenerationJobId = :id",
                Long.class
        )
                .setParameter(
                        "id",
                        jobId
                )
                .getSingleResult() > 0;
    }

    @Override
    public void deleteJob(DocumentGenerationJob job) {
        entityManager.remove(job);
        entityManager.flush();
    }

    @Override
    public void deleteInput(Transcript transcript) {
        entityManager.flush();
        entityManager.createQuery("DELETE FROM TranscriptSegment s WHERE s.transcriptId = :id")
                .setParameter(
                        "id",
                        transcript.getId()
                )
                .executeUpdate();
        entityManager.remove(transcript);
        entityManager.flush();
    }
}

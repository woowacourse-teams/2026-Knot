package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentRetentionCandidate;
import com.knot.backend.document.domain.DocumentRetentionRepository;
import com.knot.backend.recording.domain.Transcript;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class DocumentRetentionRepositoryAdapter implements DocumentRetentionRepository {

    private final DocumentGenerationBatchJpaRepository batches;
    private final DocumentGenerationJobJpaRepository jobs;
    private final DocumentGenerationInputJpaRepository inputs;
    private final DocumentJpaRepository documents;

    @Override
    public boolean belongsToRecording(
            long batchId,
            long recordingId
    ) {
        return batches.existsByIdAndRecordingSessionId(
                batchId,
                recordingId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentRetentionCandidate> findCandidates(
            Instant now,
            int limit
    ) {
        PageRequest page = PageRequest.of(
                0,
                limit
        );
        return batches.findRetentionCandidates(
                now,
                page
        );
    }

    @Override
    public List<DocumentGenerationJob> findJobsForUpdate(long batchId) {
        return jobs.findAllByBatchIdForUpdate(batchId);
    }

    @Override
    public Optional<Transcript> findInputForUpdate(long batchId) {
        return inputs.findByBatchIdForUpdate(batchId);
    }

    @Override
    public boolean hasDocumentForInput(long transcriptId) {
        return documents.existsBySourceTranscriptId(transcriptId);
    }

    @Override
    public boolean hasDocumentForJob(long jobId) {
        return documents.existsByDocumentGenerationJobId(jobId);
    }

    @Override
    public void deleteJob(DocumentGenerationJob job) {
        jobs.delete(job);
        jobs.flush();
    }

    @Override
    public void deleteInput(Transcript transcript) {
        Long transcriptId = transcript.getId();
        inputs.flush();
        inputs.deleteSegmentsByTranscriptId(transcriptId);
        inputs.delete(transcript);
        inputs.flush();
    }
}

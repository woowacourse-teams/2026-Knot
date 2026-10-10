package com.knot.backend.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.knot.backend.recording.domain.Transcript;

public interface DocumentRetentionRepository {

    List<DocumentRetentionCandidate> findCandidates(
            Instant now,
            int limit
    );

    boolean belongsToRecording(
            long batchId,
            long recordingId
    );

    List<DocumentGenerationJob> findJobsForUpdate(long batchId);

    Optional<Transcript> findInputForUpdate(long batchId);

    boolean hasDocumentForInput(long transcriptId);

    boolean hasDocumentForJob(long jobId);

    void deleteJob(DocumentGenerationJob job);

    void deleteInput(Transcript transcript);
}

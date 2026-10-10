package com.knot.backend.document.application;

import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobStage;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import com.knot.backend.document.domain.DocumentGenerationProcessingStatus;
import com.knot.backend.document.domain.DocumentRetentionRepository;
import com.knot.backend.recording.application.RecordingAudioRetentionService;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import com.knot.backend.recording.domain.Transcript;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentRetentionCleanupService {

    private final WorkspaceRepository workspaces;
    private final RecordingSessionRepository recordings;
    private final DocumentGenerationBatchRepository batches;
    private final DocumentRetentionRepository retention;
    private final RecordingAudioRetentionService audio;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cleanup(
            long workspaceId,
            long recordingId,
            long batchId
    ) {
        if (workspaces.findIncludingDeletedByIdForUpdate(workspaceId)
                .isEmpty()) {
            return;
        }
        Optional<RecordingSession> recording = recordings.findByIdForUpdate(recordingId);
        if (recording.isEmpty() || recording.orElseThrow()
                .getWorkspaceId() != workspaceId) {
            return;
        }
        if (!retention.belongsToRecording(
                batchId,
                recordingId
        )) {
            return;
        }
        List<DocumentGenerationJob> jobs = retention.findJobsForUpdate(batchId);
        Optional<Transcript> input = retention.findInputForUpdate(batchId);
        Optional<DocumentGenerationBatch> found = batches.findByIdForUpdate(batchId);
        if (found.isEmpty() || found.orElseThrow()
                .getRecordingSessionId() != recordingId) {
            return;
        }
        DocumentGenerationBatch batch = found.orElseThrow();
        Instant now = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
        deleteExpiredFailures(
                jobs,
                now
        );
        releaseUnreferencedInput(
                batch,
                input,
                now
        );
        if (batch.getProcessingStatus() == DocumentGenerationProcessingStatus.NO_CONTENT) {
            audio.scheduleNoContent(recordingId);
        }
    }

    private void deleteExpiredFailures(
            List<DocumentGenerationJob> jobs,
            Instant now
    ) {
        for (DocumentGenerationJob job : jobs) {
            if (job.isRetentionExpired(now) && !retention.hasDocumentForJob(job.getId())) {
                retention.deleteJob(job);
            }
        }
    }

    private void releaseUnreferencedInput(
            DocumentGenerationBatch batch,
            Optional<Transcript> input,
            Instant now
    ) {
        if (input.isEmpty() || !hasReleasableResult(batch)) {
            return;
        }
        Transcript transcript = input.orElseThrow();
        if (retention.hasDocumentForInput(transcript.getId())) {
            return;
        }
        List<DocumentGenerationJob> remaining = retention.findJobsForUpdate(batch.getId());
        if (remaining.stream()
                .anyMatch(this::requiresInput)) {
            return;
        }
        for (DocumentGenerationJob job : remaining) {
            retention.deleteJob(job);
        }
        batch.releaseInput(
                transcript.getId(),
                now
        );
        batches.saveAndFlush(batch);
        retention.deleteInput(transcript);
    }

    private boolean hasReleasableResult(DocumentGenerationBatch batch) {
        return batch.getProcessingStatus() == DocumentGenerationProcessingStatus.FAILED
                || batch.getProcessingStatus() == DocumentGenerationProcessingStatus.NO_CONTENT;
    }

    private boolean requiresInput(DocumentGenerationJob job) {
        return job.getStatus() != DocumentGenerationJobStatus.SUCCEEDED
                || job.getStage() != DocumentGenerationJobStage.CLASSIFICATION
                || retention.hasDocumentForJob(job.getId());
    }
}

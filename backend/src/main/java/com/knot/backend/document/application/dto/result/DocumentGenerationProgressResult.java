package com.knot.backend.document.application.dto.result;

import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationProcessingStatus;
import java.time.Instant;

public record DocumentGenerationProgressResult(
        long batchId,
        long recordingSessionId,
        DocumentGenerationProcessingStatus status,
        int queuedCount,
        int runningCount,
        int succeededCount,
        int failedCount,
        Instant finishedAt,
        Instant cleanupRequestedAt
) {

    public static DocumentGenerationProgressResult from(DocumentGenerationBatch batch) {
        return new DocumentGenerationProgressResult(
                batch.getId(),
                batch.getRecordingSessionId(),
                batch.getProcessingStatus(),
                batch.getQueuedCount(),
                batch.getRunningCount(),
                batch.getSucceededCount(),
                batch.getFailedCount(),
                batch.getFinishedAt(),
                batch.getCleanupRequestedAt()
        );
    }
}

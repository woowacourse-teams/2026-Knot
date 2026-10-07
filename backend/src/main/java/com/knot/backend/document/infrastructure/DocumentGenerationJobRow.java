package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import java.time.Instant;

record DocumentGenerationJobRow(
        long jobId,
        long recordingSessionId,
        String recordingTitle,
        DocumentGenerationJobStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}

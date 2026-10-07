package com.knot.backend.document.application.dto.result;

import com.knot.backend.document.domain.DocumentGenerationJobStatus;

public record DocumentGenerationJobRetryResult(
        long jobId,
        DocumentGenerationJobStatus status,
        int attemptCount
) {
}

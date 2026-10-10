package com.knot.backend.document.application.dto.result;

import com.knot.backend.document.domain.DocumentGenerationJobStage;

public record DocumentGenerationExecution(
        long workspaceId,
        long jobId,
        int attemptCount,
        DocumentGenerationJobStage stage,
        String transcriptContent,
        String topic
) {

    @Override
    public String toString() {
        return "DocumentGenerationExecution[jobId=" + jobId + ", stage=" + stage + ", attemptCount=" + attemptCount
                + "]";
    }
}

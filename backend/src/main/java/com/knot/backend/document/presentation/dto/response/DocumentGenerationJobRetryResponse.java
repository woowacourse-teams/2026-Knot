package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentGenerationJobRetryResult;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record DocumentGenerationJobRetryResponse(
        @Schema(description = "재접수한 기존 Job ID", requiredMode = REQUIRED) long jobId,
        @Schema(description = "접수 후 상태", allowableValues = "QUEUED", requiredMode = REQUIRED) DocumentGenerationJobStatus status,
        @Schema(description = "접수 후 누적 시도 수. 최초·사용자·자동 시도를 포함하며 사용자 한도는 별도", minimum = "1", requiredMode = REQUIRED) int attemptCount
) {

    public static DocumentGenerationJobRetryResponse from(DocumentGenerationJobRetryResult result) {
        return new DocumentGenerationJobRetryResponse(
                result.jobId(),
                result.status(),
                result.attemptCount()
        );
    }
}

package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentConfirmationResult;
import com.knot.backend.document.domain.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DocumentConfirmationResponse(
        @Schema(description = "확인한 Document ID", requiredMode = REQUIRED) long documentId,
        @Schema(description = "최초 확인 시각, UTC. 반복 요청에도 유지", requiredMode = REQUIRED) Instant confirmedAt,
        @Schema(description = "현재 문서 상태", requiredMode = REQUIRED) DocumentStatus documentStatus,
        @Schema(description = "최초 보관 시각, UTC. DRAFT이면 null", nullable = true, requiredMode = REQUIRED) Instant archivedAt,
        @Schema(description = "현재 확인 대상 집계", requiredMode = REQUIRED) DocumentConfirmationSummaryResponse confirmationSummary
) {
    public static DocumentConfirmationResponse from(DocumentConfirmationResult result) {
        return new DocumentConfirmationResponse(
                result.documentId(),
                result.confirmedAt(),
                result.documentStatus(),
                result.archivedAt(),
                DocumentConfirmationSummaryResponse.from(result.confirmationSummary())
        );
    }
}

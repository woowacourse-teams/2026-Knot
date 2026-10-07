package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record DocumentConfirmationSummaryResponse(
        @Schema(description = "확인한 대상 수. 확인 후 탈퇴한 대상도 포함", requiredMode = REQUIRED) int confirmedCount,
        @Schema(description = "현재 활성 Workspace 멤버인 미확인 대상 수", requiredMode = REQUIRED) int pendingCount,
        @Schema(description = "미확인 상태에서 탈퇴한 대상 수", requiredMode = REQUIRED) int excludedCount
) {
    public static DocumentConfirmationSummaryResponse from(DocumentConfirmationSummaryResult result) {
        return new DocumentConfirmationSummaryResponse(
                result.confirmedCount(),
                result.pendingCount(),
                result.excludedCount()
        );
    }
}

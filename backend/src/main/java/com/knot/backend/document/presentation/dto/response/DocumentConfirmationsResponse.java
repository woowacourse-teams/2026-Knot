package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentConfirmationsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DocumentConfirmationsResponse(
        @Schema(description = "Document ID", requiredMode = REQUIRED) long documentId,
        @Schema(description = "전체 대상 중 확인 완료 인원. 페이지 범위 미적용", requiredMode = REQUIRED) int confirmedCount,
        @Schema(description = "전체 대상 중 현재 활성 미확인 인원. 페이지 범위 미적용", requiredMode = REQUIRED) int pendingCount,
        @Schema(description = "전체 대상 중 탈퇴한 미확인 인원. 페이지 범위 미적용", requiredMode = REQUIRED) int excludedCount,
        @Schema(description = "현재 Member의 확인 여부. 확인 대상이 아니거나 미확인이면 false", requiredMode = REQUIRED) boolean confirmedByMe,
        @Schema(description = "생성 당시 고정된 확인 대상의 페이지", requiredMode = REQUIRED) List<DocumentConfirmationItemResponse> items,
        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", nullable = true, requiredMode = REQUIRED) String nextCursor
) {
    public static DocumentConfirmationsResponse from(DocumentConfirmationsResult result) {
        return new DocumentConfirmationsResponse(
                result.documentId(),
                result.summary()
                        .confirmedCount(),
                result.summary()
                        .pendingCount(),
                result.summary()
                        .excludedCount(),
                result.confirmedByMe(),
                result.items()
                        .stream()
                        .map(DocumentConfirmationItemResponse::from)
                        .toList(),
                result.nextCursor()
        );
    }
}

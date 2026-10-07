package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentGenerationJobListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DocumentGenerationJobListResponse(
        @Schema(description = "현재 Workspace의 진행·기한 내 실패 작업. 없으면 빈 배열", requiredMode = REQUIRED) List<DocumentGenerationJobItemResponse> items,
        @Schema(description = "다음 페이지 커서. 마지막 페이지이면 null", nullable = true, requiredMode = REQUIRED) String nextCursor
) {
    public static DocumentGenerationJobListResponse from(DocumentGenerationJobListResult result) {
        return new DocumentGenerationJobListResponse(
                result.items()
                        .stream()
                        .map(DocumentGenerationJobItemResponse::from)
                        .toList(),
                result.nextCursor()
        );
    }
}

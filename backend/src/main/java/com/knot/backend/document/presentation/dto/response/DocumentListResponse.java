package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DocumentListResponse(
        @Schema(description = "조회 조건을 만족하는 전체 주제 폴더. 0개 주제 제외", requiredMode = REQUIRED) List<DocumentTopicResponse> topics,
        @Schema(description = "최신순 카드 페이지", requiredMode = REQUIRED) List<DocumentCardResponse> items,
        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", nullable = true, requiredMode = REQUIRED) String nextCursor
) {
    public static DocumentListResponse from(DocumentListResult result) {
        return new DocumentListResponse(
                result.topics()
                        .stream()
                        .map(DocumentTopicResponse::from)
                        .toList(),
                result.items()
                        .stream()
                        .map(DocumentCardResponse::from)
                        .toList(),
                result.nextCursor()
        );
    }
}

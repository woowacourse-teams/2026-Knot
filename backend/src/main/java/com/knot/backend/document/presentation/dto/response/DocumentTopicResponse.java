package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentTopicResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record DocumentTopicResponse(
        @Schema(description = "AI가 분류한 주제 폴더 이름", requiredMode = REQUIRED) String topic,
        @Schema(description = "모든 조회 조건을 만족하는 주제 전체 문서 수. 페이지 범위 제외", requiredMode = REQUIRED) int documentCount
) {
    public static DocumentTopicResponse from(DocumentTopicResult result) {
        return new DocumentTopicResponse(
                result.topic(),
                result.documentCount()
        );
    }
}

package com.knot.backend.search.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.search.application.dto.result.SearchEvidenceItemResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record SearchEvidenceItemResponse(
        @Schema(requiredMode = REQUIRED) long documentId,
        @Schema(requiredMode = REQUIRED) String title,
        @Schema(requiredMode = REQUIRED) String topic,
        @Schema(minimum = "1", maximum = "3", requiredMode = REQUIRED) int rank
) {

    public static SearchEvidenceItemResponse from(SearchEvidenceItemResult result) {
        return new SearchEvidenceItemResponse(
                result.documentId(),
                result.title(),
                result.topic(),
                result.rank()
        );
    }
}

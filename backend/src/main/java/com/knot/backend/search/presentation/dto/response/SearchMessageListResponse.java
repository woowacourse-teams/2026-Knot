package com.knot.backend.search.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.search.application.dto.result.SearchMessageListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SearchMessageListResponse(
        @Schema(requiredMode = REQUIRED) long conversationId,
        @Schema(description = "sequence 오름차순 메시지", requiredMode = REQUIRED) List<SearchMessageListItemResponse> items,
        @Schema(requiredMode = REQUIRED) boolean hasPrevious,
        @Schema(description = "이전 페이지 경계 sequence의 십진 문자열. 마지막 페이지면 null", nullable = true, requiredMode = REQUIRED) String previousCursor
) {

    public static SearchMessageListResponse from(SearchMessageListResult result) {
        return new SearchMessageListResponse(
                result.conversationId(),
                result.items()
                        .stream()
                        .map(SearchMessageListItemResponse::from)
                        .toList(),
                result.hasPrevious(),
                result.previousCursor()
        );
    }
}

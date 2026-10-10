package com.knot.backend.search.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.search.application.dto.result.SearchConversationListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SearchConversationListResponse(
        @Schema(description = "내 대화 목록. updatedAt DESC, id DESC", requiredMode = REQUIRED) List<SearchConversationListItemResponse> items,
        @Schema(description = "다음 페이지의 불투명 커서. 마지막 페이지면 null", nullable = true, requiredMode = REQUIRED) String nextCursor
) {

    public static SearchConversationListResponse from(SearchConversationListResult result) {
        return new SearchConversationListResponse(
                result.items()
                        .stream()
                        .map(SearchConversationListItemResponse::from)
                        .toList(),
                result.nextCursor()
        );
    }
}

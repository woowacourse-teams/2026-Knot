package com.knot.backend.search.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.search.application.dto.result.SearchConversationListItemResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record SearchConversationListItemResponse(
        @Schema(description = "SearchConversation ID", requiredMode = REQUIRED) long id,
        @Schema(description = "첫 USER 질문의 표시 제목. 공백 정리 후 최대 80 코드 포인트", requiredMode = REQUIRED) String title,
        @Schema(description = "최신 메시지 미리보기. 최대 120 코드 포인트", nullable = true, requiredMode = REQUIRED) String lastMessagePreview,
        @Schema(description = "첫 질문 접수 시각", requiredMode = REQUIRED) Instant createdAt,
        @Schema(description = "최근 메시지 활동 시각", requiredMode = REQUIRED) Instant updatedAt
) {

    public static SearchConversationListItemResponse from(SearchConversationListItemResult result) {
        return new SearchConversationListItemResponse(
                result.id(),
                result.title(),
                result.lastMessagePreview(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}

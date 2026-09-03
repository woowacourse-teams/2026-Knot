package com.knot.backend.chat.presentation.dto.response;

import com.knot.backend.search.domain.SearchReference;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 답변의 검색 출처")
public record SearchReferenceResponse(
        @Schema(description = "검색 출처 ID", example = "1") long id,
        @Schema(description = "출처가 연결된 메시지 ID", example = "102") long messageId,
        @Schema(description = "메시지 내 관련도 순위", example = "1") int rank,
        @Schema(description = "검색 관련도 점수", example = "0.9472") double relevanceScore,
        @Schema(description = "출처 제공자", example = "NOTION") ContentSourceProvider source,
        @Schema(description = "원본 출처 문서") NotionPageReferenceResponse notionPage
) {

    public static SearchReferenceResponse from(SearchReference reference) {
        SearchReference.NotionPageReference notionPage = reference.notionPage();
        return new SearchReferenceResponse(
                reference.id(),
                reference.messageId(),
                reference.rank(),
                reference.relevanceScore(),
                reference.source(),
                new NotionPageReferenceResponse(
                        notionPage.id(),
                        notionPage.title(),
                        notionPage.notionUrl(),
                        notionPage.createdAt(),
                        notionPage.updatedAt()
                )
        );
    }
}

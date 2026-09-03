package com.knot.backend.chat.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "검색 출처 Notion 페이지 메타데이터")
public record NotionPageReferenceResponse(
        @Schema(description = "원본 Notion 페이지 ID", example = "14f2a8c1-7e3b-4d6a-9f21-8c5b0a12d934") String id,
        @Schema(description = "Notion 페이지 제목", example = "기술 스택과 라이브러리 도입") String title,
        @Schema(description = "원본 Notion 페이지 URL", example = "https://www.notion.so/example") String notionUrl,
        @Schema(description = "Notion 페이지 생성 시각", example = "2026-08-30T00:00:00Z") Instant createdAt,
        @Schema(description = "Notion 페이지 수정 시각", example = "2026-09-01T04:12:35Z") Instant updatedAt
) {
}

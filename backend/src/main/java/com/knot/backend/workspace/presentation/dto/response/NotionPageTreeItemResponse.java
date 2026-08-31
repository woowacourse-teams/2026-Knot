package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.NotionPageTreeItemResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notion Page Tree 항목")
public record NotionPageTreeItemResponse(
        @Schema(description = "Knot Page ID", example = "1") long id,
        @Schema(description = "부모 Knot Page ID, 최상위 Page이면 null", example = "1", nullable = true) Long parentPageId,
        @Schema(description = "Page 제목", example = "프로젝트 소개") String title,
        @Schema(description = "같은 부모 아래 Page 순서", example = "0") int position,
        @Schema(description = "원본 Notion Page URL", example = "https://www.notion.so/example") String notionUrl
) {

    public static NotionPageTreeItemResponse from(NotionPageTreeItemResult result) {
        return new NotionPageTreeItemResponse(
                result.id(),
                result.parentPageId(),
                result.title(),
                result.position(),
                result.notionUrl()
        );
    }
}

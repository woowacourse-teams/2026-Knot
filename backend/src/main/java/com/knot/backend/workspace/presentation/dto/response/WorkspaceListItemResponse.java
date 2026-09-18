package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceListItemResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "워크스페이스 목록 항목")
public record WorkspaceListItemResponse(
        @Schema(description = "워크스페이스 ID", example = "1") long id,
        @Schema(description = "워크스페이스 이름", example = "Knot 팀") String name
) {

    public static WorkspaceListItemResponse from(WorkspaceListItemResult result) {
        return new WorkspaceListItemResponse(
                result.id(),
                result.name()
        );
    }
}

package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내 워크스페이스 목록 응답")
public record WorkspaceListResponse(
        @Schema(description = "마지막으로 본 워크스페이스 ID", nullable = true) Long lastViewedWorkspaceId,
        @Schema(description = "내가 속한 워크스페이스 목록") List<WorkspaceListItemResponse> workspaces
) {

    public static WorkspaceListResponse from(WorkspaceListResult result) {
        return new WorkspaceListResponse(
                result.lastViewedWorkspaceId(),
                result.workspaces()
                        .stream()
                        .map(WorkspaceListItemResponse::from)
                        .toList()
        );
    }
}

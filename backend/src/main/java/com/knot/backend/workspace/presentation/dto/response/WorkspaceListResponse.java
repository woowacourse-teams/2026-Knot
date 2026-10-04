package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내 워크스페이스 목록 응답")
public record WorkspaceListResponse(
        @Schema(description = "목록 안의 마지막 조회 ID, 없거나 탈퇴·삭제됐으면 null", nullable = true) Long lastViewedWorkspaceId,
        @Schema(description = "활성 참여·비삭제 목록, 참여 시각 내림차순·동률이면 ID 내림차순") List<WorkspaceListItemResponse> workspaces
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

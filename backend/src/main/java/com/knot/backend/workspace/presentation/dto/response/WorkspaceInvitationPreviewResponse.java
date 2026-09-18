package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationPreviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "워크스페이스 초대 미리보기 응답")
public record WorkspaceInvitationPreviewResponse(
        @Schema(description = "워크스페이스 ID", example = "1") Long workspaceId,
        @Schema(description = "워크스페이스 이름", example = "knot") String workspaceName
) {

    public static WorkspaceInvitationPreviewResponse from(WorkspaceInvitationPreviewResult result) {
        return new WorkspaceInvitationPreviewResponse(
                result.workspaceId(),
                result.workspaceName()
        );
    }
}

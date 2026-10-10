package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceDetailResult;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record WorkspaceDetailResponse(
        @Schema(description = "워크스페이스 이름", example = "Knot 팀") String name,
        @Schema(description = "요청한 회원의 역할", example = "OWNER") WorkspaceMemberRole myRole,
        @Schema(description = "요청한 회원을 포함해 탈퇴하지 않은 멤버 수", example = "2") long activeMemberCount
) {

    public static WorkspaceDetailResponse from(WorkspaceDetailResult result) {
        return new WorkspaceDetailResponse(
                result.name(),
                result.myRole(),
                result.activeMemberCount()
        );
    }
}

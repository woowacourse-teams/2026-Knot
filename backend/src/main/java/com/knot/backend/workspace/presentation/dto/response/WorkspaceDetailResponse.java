package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceDetailResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record WorkspaceDetailResponse(@Schema(description = "워크스페이스 이름", example = "Knot 팀") String name) {

    public static WorkspaceDetailResponse from(WorkspaceDetailResult result) {
        return new WorkspaceDetailResponse(result.name());
    }
}

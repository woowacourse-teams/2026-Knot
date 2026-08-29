package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceDetailResult;

public record WorkspaceDetailResponse(String name) {

    public static WorkspaceDetailResponse from(WorkspaceDetailResult result) {
        return new WorkspaceDetailResponse(result.name());
    }
}

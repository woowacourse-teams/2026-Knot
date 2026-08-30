package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.Workspace;

public record WorkspaceDetailResult(String name) {

    public static WorkspaceDetailResult from(Workspace workspace) {
        return new WorkspaceDetailResult(workspace.getName());
    }
}

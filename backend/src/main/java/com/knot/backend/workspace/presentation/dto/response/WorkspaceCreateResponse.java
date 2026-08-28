package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceCreateResult;

public record WorkspaceCreateResponse(long id) {

    public static WorkspaceCreateResponse from(WorkspaceCreateResult result) {
        return new WorkspaceCreateResponse(result.id());
    }
}

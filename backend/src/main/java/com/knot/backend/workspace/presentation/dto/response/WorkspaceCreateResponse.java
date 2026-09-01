package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceCreateResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record WorkspaceCreateResponse(@Schema(description = "생성된 워크스페이스 ID", example = "1") long id) {

    public static WorkspaceCreateResponse from(WorkspaceCreateResult result) {
        return new WorkspaceCreateResponse(result.id());
    }
}

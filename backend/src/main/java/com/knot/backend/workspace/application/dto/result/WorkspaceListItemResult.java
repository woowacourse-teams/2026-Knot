package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.Workspace;

public record WorkspaceListItemResult(
        long id,
        String name
) {

    public static WorkspaceListItemResult from(Workspace workspace) {
        return new WorkspaceListItemResult(
                workspace.getId(),
                workspace.getName()
        );
    }
}

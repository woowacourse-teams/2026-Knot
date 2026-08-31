package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.Workspace;
import java.util.List;

public record WorkspaceListResult(List<WorkspaceListItemResult> workspaces) {

    public static WorkspaceListResult from(List<Workspace> workspaces) {
        return new WorkspaceListResult(
                workspaces.stream()
                        .map(WorkspaceListItemResult::from)
                        .toList()
        );
    }
}

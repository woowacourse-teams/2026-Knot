package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.Workspace;
import java.util.List;

public record WorkspaceListResult(
        Long lastViewedWorkspaceId,
        List<WorkspaceListItemResult> workspaces
) {

    public static WorkspaceListResult from(
            Long lastViewedWorkspaceId,
            List<Workspace> workspaces
    ) {
        return new WorkspaceListResult(
                lastViewedWorkspaceId,
                workspaces.stream()
                        .map(WorkspaceListItemResult::from)
                        .toList()
        );
    }
}

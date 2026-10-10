package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.WorkspaceMemberRole;

public record WorkspaceDetailResult(
        String name,
        WorkspaceMemberRole myRole,
        long activeMemberCount
) {

    public static WorkspaceDetailResult from(WorkspaceDetailSnapshot snapshot) {
        return new WorkspaceDetailResult(
                snapshot.name(),
                snapshot.myRole(),
                snapshot.activeMemberCount()
        );
    }
}

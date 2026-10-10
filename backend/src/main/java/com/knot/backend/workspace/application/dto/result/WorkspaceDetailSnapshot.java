package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.WorkspaceMemberRole;

public record WorkspaceDetailSnapshot(
        String name,
        WorkspaceMemberRole myRole,
        long activeMemberCount
) {

    public boolean isMember() {
        return myRole != null;
    }
}

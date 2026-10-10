package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceDetailSnapshot;
import java.util.Optional;

public interface WorkspaceDetailQuery {

    Optional<WorkspaceDetailSnapshot> find(
            long workspaceId,
            long memberId
    );
}

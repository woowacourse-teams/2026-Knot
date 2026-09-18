package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.ContentImportRun;

public record ClaimedContentImportRun(
        Long importRunId,
        Long workspaceId,
        Long contentSourceConnectionId
) {

    public static ClaimedContentImportRun from(ContentImportRun importRun) {
        return new ClaimedContentImportRun(
                importRun.getId(),
                importRun.getWorkspaceId(),
                importRun.getContentSourceConnectionId()
        );
    }
}

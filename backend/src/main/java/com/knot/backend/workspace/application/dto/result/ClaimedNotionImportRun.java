package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.NotionImportRun;

public record ClaimedNotionImportRun(
        Long importRunId,
        Long workspaceId,
        Long contentSourceConnectionId
) {

    public static ClaimedNotionImportRun from(NotionImportRun importRun) {
        return new ClaimedNotionImportRun(
                importRun.getId(),
                importRun.getWorkspaceId(),
                importRun.getContentSourceConnectionId()
        );
    }
}

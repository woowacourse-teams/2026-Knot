package com.knot.backend.workspace.application.dto.result;

public record NotionImportRecoveryResult(
        int pendingCount,
        int runningCount
) {

    public int totalCount() {
        return pendingCount + runningCount;
    }
}

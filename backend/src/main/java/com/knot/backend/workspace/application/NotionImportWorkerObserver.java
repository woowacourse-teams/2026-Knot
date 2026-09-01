package com.knot.backend.workspace.application;

public interface NotionImportWorkerObserver {

    void claimed(
            Long importRunId,
            Long workspaceId
    );

    void completed(
            Long importRunId,
            Long workspaceId,
            int pageCount
    );

    void failed(
            Long importRunId,
            Long workspaceId,
            NotionImportFailureCategory category
    );

    void staleRecovered(
            int pendingCount,
            int runningCount
    );

    void pollingFailed();
}

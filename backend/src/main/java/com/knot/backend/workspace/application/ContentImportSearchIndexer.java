package com.knot.backend.workspace.application;

public interface ContentImportSearchIndexer {

    void index(
            Long importRunId,
            Long workspaceId
    );
}

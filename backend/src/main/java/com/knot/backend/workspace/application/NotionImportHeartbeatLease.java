package com.knot.backend.workspace.application;

public interface NotionImportHeartbeatLease {

    Handle start(
            Long importRunId,
            Long workspaceId
    );

    interface Handle extends AutoCloseable {

        boolean isActive();

        @Override
        void close();
    }
}

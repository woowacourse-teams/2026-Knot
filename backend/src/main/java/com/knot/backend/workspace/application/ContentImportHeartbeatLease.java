package com.knot.backend.workspace.application;

public interface ContentImportHeartbeatLease {

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

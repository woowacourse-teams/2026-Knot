package com.knot.backend.workspace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContentImportRunRepository {

    ContentImportRun save(ContentImportRun importRun);

    Optional<ContentImportRun> findFirstPendingForUpdate();

    Optional<ContentImportRun> findByIdForUpdate(Long importRunId);

    boolean heartbeatIfRunning(Long importRunId);

    Instant currentDatabaseTime();

    List<ContentImportRun> findStaleRunningForUpdate(
            long runningTimeoutMillis,
            int batchSize
    );

    Optional<ContentImportRun> findActiveByContentSourceConnectionId(Long contentSourceConnectionId);

    Optional<ContentImportRun> findVisibleByIdAndMemberId(
            Long importRunId,
            long memberId
    );
}

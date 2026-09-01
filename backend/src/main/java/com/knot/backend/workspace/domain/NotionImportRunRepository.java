package com.knot.backend.workspace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotionImportRunRepository {

    NotionImportRun save(NotionImportRun importRun);

    Optional<NotionImportRun> findFirstPendingForUpdate();

    Optional<NotionImportRun> findByIdForUpdate(Long importRunId);

    boolean heartbeatIfRunning(Long importRunId);

    Instant currentDatabaseTime();

    List<NotionImportRun> findStaleRunningForUpdate(
            long runningTimeoutMillis,
            int batchSize
    );

    Optional<NotionImportRun> findVisibleByIdAndMemberId(
            Long importRunId,
            long memberId
    );
}

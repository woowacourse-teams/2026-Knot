package com.knot.backend.workspace.domain;

import java.util.Optional;

public interface ContentImportRunRepository {

    ContentImportRun save(ContentImportRun importRun);

    Optional<ContentImportRun> findActiveByContentSourceConnectionId(Long contentSourceConnectionId);

    Optional<ContentImportRun> findVisibleByIdAndMemberId(
            Long importRunId,
            long memberId
    );
}

package com.knot.backend.workspace.domain;

import java.util.Optional;

public interface NotionImportRunRepository {

    NotionImportRun save(NotionImportRun importRun);

    Optional<NotionImportRun> findActiveByContentSourceConnectionId(Long contentSourceConnectionId);

    Optional<NotionImportRun> findVisibleByIdAndMemberId(
            Long importRunId,
            long memberId
    );
}

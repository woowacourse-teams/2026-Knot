package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportStatus;
import java.time.Instant;

public record ContentImportStatusResult(
        long id,
        ContentImportStatus status,
        Integer totalPageCount,
        int processedPageCount,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {

    public static ContentImportStatusResult from(ContentImportRun importRun) {
        return new ContentImportStatusResult(
                importRun.getId(),
                importRun.getStatus(),
                importRun.getTotalPageCount(),
                importRun.getProcessedPageCount(),
                importRun.getCreatedAt(),
                importRun.getStartedAt(),
                importRun.getCompletedAt()
        );
    }
}

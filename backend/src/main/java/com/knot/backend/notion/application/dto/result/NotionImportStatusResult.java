package com.knot.backend.notion.application.dto.result;

import com.knot.backend.notion.domain.NotionImportRun;
import com.knot.backend.notion.domain.NotionImportStatus;
import java.time.Instant;

public record NotionImportStatusResult(
        long id,
        NotionImportStatus status,
        Integer totalPageCount,
        int processedPageCount,
        String failureReason,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {

    public static NotionImportStatusResult from(NotionImportRun importRun) {
        return new NotionImportStatusResult(
                importRun.getId(),
                importRun.getStatus(),
                importRun.getTotalPageCount(),
                importRun.getProcessedPageCount(),
                importRun.publicFailureReason(),
                importRun.getCreatedAt(),
                importRun.getStartedAt(),
                importRun.getCompletedAt()
        );
    }
}

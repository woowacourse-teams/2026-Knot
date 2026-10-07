package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentGenerationJobListQuery;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobItemResult;
import com.knot.backend.document.domain.DocumentGenerationJobCursor;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentGenerationJobListQueryAdapter implements DocumentGenerationJobListQuery {
    private static final List<DocumentGenerationJobStatus> ACTIVE_STATUSES = List.of(
            DocumentGenerationJobStatus.QUEUED,
            DocumentGenerationJobStatus.RUNNING
    );
    private final DocumentGenerationJobReadJpaRepository jobs;

    @Override
    public List<DocumentGenerationJobItemResult> findPage(
            long workspaceId,
            int limit,
            DocumentGenerationJobCursor cursor,
            Instant now
    ) {
        return jobs.findPage(
                workspaceId,
                ACTIVE_STATUSES,
                DocumentGenerationJobStatus.FAILED,
                now,
                cursor != null,
                cursorCreatedAt(cursor),
                cursorJobId(cursor),
                PageRequest.of(
                        0,
                        limit
                )
        )
                .stream()
                .map(this::toResult)
                .toList();
    }

    private Instant cursorCreatedAt(DocumentGenerationJobCursor cursor) {
        if (cursor == null) {
            return Instant.EPOCH;
        }
        return cursor.getCreatedAt();
    }

    private long cursorJobId(DocumentGenerationJobCursor cursor) {
        if (cursor == null) {
            return 0;
        }
        return cursor.getJobId();
    }

    private DocumentGenerationJobItemResult toResult(DocumentGenerationJobRow row) {
        return new DocumentGenerationJobItemResult(
                row.jobId(),
                row.recordingSessionId(),
                row.recordingTitle(),
                row.status(),
                row.createdAt(),
                row.updatedAt()
        );
    }
}

package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentGenerationJobItemResult;
import com.knot.backend.document.domain.DocumentGenerationJobCursor;
import java.time.Instant;
import java.util.List;

public interface DocumentGenerationJobListQuery {

    List<DocumentGenerationJobItemResult> findPage(
            long workspaceId,
            long memberId,
            int limit,
            DocumentGenerationJobCursor cursor,
            Instant now
    );
}

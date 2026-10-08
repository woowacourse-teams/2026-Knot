package com.knot.backend.document.domain;

import java.util.Optional;
import java.util.List;

public interface DocumentGenerationJobRepository {

    DocumentGenerationJob save(DocumentGenerationJob job);

    List<DocumentGenerationJob> findAllByBatchId(long batchId);

    Optional<DocumentGenerationJob> findByWorkspaceIdAndIdForUpdate(
            long workspaceId,
            long jobId
    );

    void flush();
}

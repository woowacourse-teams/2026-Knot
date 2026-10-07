package com.knot.backend.document.domain;

import java.util.Optional;

public interface DocumentGenerationJobRepository {

    Optional<DocumentGenerationJob> findByWorkspaceIdAndIdForUpdate(
            long workspaceId,
            long jobId
    );

    void flush();
}

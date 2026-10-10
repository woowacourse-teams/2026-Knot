package com.knot.backend.document.domain;

import java.util.Optional;

public interface DocumentGenerationBatchRepository {

    Optional<DocumentGenerationBatch> findByRecordingSessionIdForUpdate(long recordingSessionId);

    Optional<DocumentGenerationBatch> findByIdForUpdate(long batchId);

    DocumentGenerationBatch saveAndFlush(DocumentGenerationBatch batch);

    Optional<DocumentGenerationBatch> findByWorkspaceIdAndRecordingSessionId(
            long workspaceId,
            long recordingSessionId
    );
}

package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentGenerationBatchRepositoryAdapter implements DocumentGenerationBatchRepository {

    private final DocumentGenerationBatchJpaRepository repository;

    @Override
    public Optional<DocumentGenerationBatch> findByRecordingSessionIdForUpdate(long recordingSessionId) {
        return repository.findByRecordingSessionId(recordingSessionId);
    }

    @Override
    public Optional<DocumentGenerationBatch> findByIdForUpdate(long batchId) {
        return repository.findLockedById(batchId);
    }

    @Override
    public DocumentGenerationBatch saveAndFlush(DocumentGenerationBatch batch) {
        return repository.saveAndFlush(batch);
    }
}

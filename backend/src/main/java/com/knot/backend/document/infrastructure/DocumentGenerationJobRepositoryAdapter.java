package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import java.util.Optional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentGenerationJobRepositoryAdapter implements DocumentGenerationJobRepository {

    private final DocumentGenerationJobJpaRepository repository;

    @Override
    public DocumentGenerationJob save(DocumentGenerationJob job) {
        return repository.save(job);
    }

    @Override
    public List<DocumentGenerationJob> findAllByBatchId(long batchId) {
        return repository.findAllByBatchIdOrderById(batchId);
    }

    @Override
    public Optional<DocumentGenerationJob> findByWorkspaceIdAndIdForUpdate(
            long workspaceId,
            long jobId
    ) {
        return repository.findByWorkspaceIdAndId(
                workspaceId,
                jobId
        );
    }

    @Override
    public void flush() {
        repository.flush();
    }
}

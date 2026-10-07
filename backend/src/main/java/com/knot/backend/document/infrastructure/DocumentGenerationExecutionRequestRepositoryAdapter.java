package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationExecutionRequest;
import com.knot.backend.document.domain.DocumentGenerationExecutionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentGenerationExecutionRequestRepositoryAdapter
        implements
            DocumentGenerationExecutionRequestRepository {

    private final DocumentGenerationExecutionRequestJpaRepository repository;

    @Override
    public DocumentGenerationExecutionRequest save(DocumentGenerationExecutionRequest request) {
        return repository.save(request);
    }

    @Override
    public void flush() {
        repository.flush();
    }
}

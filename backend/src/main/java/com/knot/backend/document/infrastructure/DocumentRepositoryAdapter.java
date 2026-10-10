package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.Document;
import com.knot.backend.document.domain.DocumentRepository;
import com.knot.backend.document.domain.DocumentStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentRepositoryAdapter implements DocumentRepository {

    private final DocumentJpaRepository repository;

    @Override
    public Document save(Document document) {
        return repository.save(document);
    }

    @Override
    public Optional<Document> findByGenerationJobId(long jobId) {
        return repository.findByDocumentGenerationJobId(jobId);
    }

    @Override
    public Optional<Document> findByWorkspaceIdAndIdForUpdate(
            long workspaceId,
            long documentId
    ) {
        return repository.findByWorkspaceIdAndId(
                workspaceId,
                documentId
        );
    }

    @Override
    public List<Document> findAffectedDraftsForUpdate(
            long workspaceId,
            long memberId
    ) {
        return repository.findAffectedDrafts(
                workspaceId,
                memberId,
                DocumentStatus.DRAFT
        );
    }

    @Override
    public void flush() {
        repository.flush();
    }
}

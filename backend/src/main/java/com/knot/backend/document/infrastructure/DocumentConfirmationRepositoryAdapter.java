package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentConfirmation;
import com.knot.backend.document.domain.DocumentConfirmationId;
import com.knot.backend.document.domain.DocumentConfirmationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentConfirmationRepositoryAdapter implements DocumentConfirmationRepository {
    private final DocumentConfirmationJpaRepository repository;

    @Override
    public Optional<DocumentConfirmation> findByDocumentIdAndMemberId(
            long documentId,
            long memberId
    ) {
        return repository.findById(
                DocumentConfirmationId.of(
                        documentId,
                        memberId
                )
        );
    }

    @Override
    public void flush() {
        repository.flush();
    }
}

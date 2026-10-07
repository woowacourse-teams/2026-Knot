package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentGenerationInputQuery;
import com.knot.backend.document.application.dto.result.DocumentGenerationInputResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentGenerationInputQueryAdapter implements DocumentGenerationInputQuery {

    private final DocumentGenerationInputJpaRepository repository;

    @Override
    public Optional<DocumentGenerationInputResult> findForUpdate(
            long workspaceId,
            long transcriptId
    ) {
        return repository.findByWorkspaceIdAndId(
                workspaceId,
                transcriptId
        )
                .map(
                        transcript -> new DocumentGenerationInputResult(
                                transcript.getId(),
                                transcript.getContent()
                        )
                );
    }
}

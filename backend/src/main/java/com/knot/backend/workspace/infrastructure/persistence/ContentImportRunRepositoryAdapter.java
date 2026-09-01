package com.knot.backend.workspace.infrastructure.persistence;

import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentImportRunRepositoryAdapter implements ContentImportRunRepository {
    private final ContentImportRunJpaRepository importRunJpaRepository;

    @Override
    public ContentImportRun save(ContentImportRun importRun) {
        return importRunJpaRepository.save(importRun);
    }

    @Override
    public Optional<ContentImportRun> findVisibleByIdAndMemberId(
            Long importRunId,
            long memberId
    ) {
        return importRunJpaRepository.findVisibleByIdAndMemberId(
                importRunId,
                memberId
        );
    }
}

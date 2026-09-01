package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotionImportRunRepositoryAdapter implements NotionImportRunRepository {
    private final NotionImportRunJpaRepository importRunJpaRepository;

    @Override
    public NotionImportRun save(NotionImportRun importRun) {
        return importRunJpaRepository.save(importRun);
    }

    @Override
    public Optional<NotionImportRun> findVisibleByIdAndMemberId(
            Long importRunId,
            long memberId
    ) {
        return importRunJpaRepository.findVisibleByIdAndMemberId(
                importRunId,
                memberId
        );
    }
}

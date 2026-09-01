package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import java.time.Instant;
import java.util.List;
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
    public Optional<NotionImportRun> findFirstPendingForUpdate() {
        return importRunJpaRepository.findFirstPendingForUpdate();
    }

    @Override
    public Optional<NotionImportRun> findByIdForUpdate(Long importRunId) {
        return importRunJpaRepository.findByIdForUpdate(importRunId);
    }

    @Override
    public List<NotionImportRun> findStaleForUpdate(
            Instant pendingCutoff,
            Instant runningCutoff,
            int batchSize
    ) {
        return importRunJpaRepository.findStaleForUpdate(
                pendingCutoff,
                runningCutoff,
                batchSize
        );
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

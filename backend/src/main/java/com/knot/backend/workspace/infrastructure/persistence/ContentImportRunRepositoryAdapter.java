package com.knot.backend.workspace.infrastructure.persistence;

import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import java.time.Instant;
import java.util.List;
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
    public Optional<ContentImportRun> findFirstPendingForUpdate() {
        return importRunJpaRepository.findFirstPendingForUpdate();
    }

    @Override
    public Optional<ContentImportRun> findByIdForUpdate(Long importRunId) {
        return importRunJpaRepository.findByIdForUpdate(importRunId);
    }

    @Override
    public boolean heartbeatIfRunning(Long importRunId) {
        return importRunJpaRepository.heartbeatIfRunning(importRunId) == 1;
    }

    @Override
    public Instant currentDatabaseTime() {
        return importRunJpaRepository.currentDatabaseTime();
    }

    @Override
    public List<ContentImportRun> findStaleRunningForUpdate(
            long runningTimeoutMillis,
            int batchSize
    ) {
        return importRunJpaRepository.findStaleRunningForUpdate(
                runningTimeoutMillis,
                batchSize
        );
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

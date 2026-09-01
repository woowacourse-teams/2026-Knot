package com.knot.backend.workspace.infrastructure.persistence;

import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import com.knot.backend.workspace.domain.ContentImportStatus;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentImportRunRepositoryAdapter implements ContentImportRunRepository {
    private static final Set<ContentImportStatus> ACTIVE_STATUSES = Set.of(
            ContentImportStatus.PENDING,
            ContentImportStatus.RUNNING
    );
    private final ContentImportRunJpaRepository importRunJpaRepository;

    @Override
    public ContentImportRun save(ContentImportRun importRun) {
        return importRunJpaRepository.save(importRun);
    }

    @Override
    public Optional<ContentImportRun> findActiveByContentSourceConnectionId(Long contentSourceConnectionId) {
        return importRunJpaRepository.findFirstByContentSourceConnectionIdAndStatusIn(
                contentSourceConnectionId,
                ACTIVE_STATUSES
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

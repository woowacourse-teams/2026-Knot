package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.NotionImportStatus;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotionImportRunRepositoryAdapter implements NotionImportRunRepository {
    private static final Set<NotionImportStatus> ACTIVE_STATUSES = Set.of(
            NotionImportStatus.PENDING,
            NotionImportStatus.RUNNING
    );
    private final NotionImportRunJpaRepository importRunJpaRepository;

    @Override
    public NotionImportRun save(NotionImportRun importRun) {
        return importRunJpaRepository.saveAndFlush(importRun);
    }

    @Override
    public Optional<NotionImportRun> findActiveByContentSourceConnectionId(Long contentSourceConnectionId) {
        return importRunJpaRepository.findFirstByContentSourceConnectionIdAndStatusIn(
                contentSourceConnectionId,
                ACTIVE_STATUSES
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

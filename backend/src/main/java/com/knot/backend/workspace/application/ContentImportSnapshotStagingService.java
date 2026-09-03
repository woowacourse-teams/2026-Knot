package com.knot.backend.workspace.application;

import com.knot.backend.workspace.domain.ContentImportErrorCode;
import com.knot.backend.workspace.domain.ContentImportException;
import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import com.knot.backend.workspace.domain.ContentImportStatus;
import com.knot.backend.workspace.domain.ImportedPage;
import com.knot.backend.workspace.domain.ImportedPageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentImportSnapshotStagingService {
    private final ContentImportRunRepository importRunRepository;
    private final ImportedPageRepository importedPageRepository;
    private final Clock clock;

    @Transactional
    public void prepare(
            Long importRunId,
            Long workspaceId,
            int totalPageCount
    ) {
        ContentImportRun importRun = findRunningImportRun(
                importRunId,
                workspaceId
        );
        importRun.preparePageCount(totalPageCount);
        importRunRepository.save(importRun);
    }

    @Transactional
    public Long stagePage(
            Long importRunId,
            Long workspaceId,
            String externalPageId,
            String parentExternalPageId,
            String title,
            String markdownContent,
            int position,
            String sourceUrl
    ) {
        ContentImportRun importRun = findRunningImportRun(
                importRunId,
                workspaceId
        );
        Instant stagedAt = currentTime();
        ImportedPage importedPage = ImportedPage.create(
                workspaceId,
                importRunId,
                externalPageId,
                parentExternalPageId,
                title,
                markdownContent,
                position,
                sourceUrl,
                stagedAt,
                stagedAt
        );
        ImportedPage savedPage = importedPageRepository.save(importedPage);
        importRun.recordProcessedPage();
        importRunRepository.save(importRun);
        return savedPage.getId();
    }

    private ContentImportRun findRunningImportRun(
            Long importRunId,
            Long workspaceId
    ) {
        ContentImportRun importRun = importRunRepository.findByIdForUpdate(importRunId)
                .orElseThrow(this::invalidImportRun);
        if (!Objects.equals(
                importRun.getWorkspaceId(),
                workspaceId
        ) || importRun.getStatus() != ContentImportStatus.RUNNING) {
            throw invalidImportRun();
        }
        return importRun;
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private ContentImportException invalidImportRun() {
        return new ContentImportException(ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN);
    }
}

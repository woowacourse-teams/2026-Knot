package com.knot.backend.workspace.application;

import com.knot.backend.workspace.domain.NotionImportErrorCode;
import com.knot.backend.workspace.domain.NotionImportException;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.NotionImportStatus;
import com.knot.backend.workspace.domain.NotionPage;
import com.knot.backend.workspace.domain.NotionPageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotionImportSnapshotStagingService {
    private final NotionImportRunRepository importRunRepository;
    private final NotionPageRepository notionPageRepository;
    private final Clock clock;

    @Transactional
    public void prepare(
            Long importRunId,
            Long workspaceId,
            int totalPageCount
    ) {
        NotionImportRun importRun = findRunningImportRun(
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
            String notionPageId,
            Long parentPageId,
            String title,
            String markdownContent,
            int position,
            String notionUrl
    ) {
        NotionImportRun importRun = findRunningImportRun(
                importRunId,
                workspaceId
        );
        Instant stagedAt = currentTime();
        NotionPage notionPage = NotionPage.create(
                workspaceId,
                importRunId,
                notionPageId,
                parentPageId,
                title,
                markdownContent,
                position,
                notionUrl,
                stagedAt,
                stagedAt
        );
        NotionPage savedPage = notionPageRepository.save(notionPage);
        importRun.recordProcessedPage();
        importRunRepository.save(importRun);
        return savedPage.getId();
    }

    private NotionImportRun findRunningImportRun(
            Long importRunId,
            Long workspaceId
    ) {
        NotionImportRun importRun = importRunRepository.findByIdForUpdate(importRunId)
                .orElseThrow(this::invalidImportRun);
        if (!Objects.equals(
                importRun.getWorkspaceId(),
                workspaceId
        ) || importRun.getStatus() != NotionImportStatus.RUNNING) {
            throw invalidImportRun();
        }
        return importRun;
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private NotionImportException invalidImportRun() {
        return new NotionImportException(NotionImportErrorCode.INVALID_NOTION_IMPORT_RUN);
    }
}

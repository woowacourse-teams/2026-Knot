package com.knot.backend.workspace.application;

import com.knot.backend.workspace.domain.NotionImportErrorCode;
import com.knot.backend.workspace.domain.NotionImportException;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.NotionPageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotionImportPublicationService {
    private final NotionImportRunRepository importRunRepository;
    private final NotionPageRepository notionPageRepository;
    private final Clock clock;

    @Transactional
    public void publish(Long importRunId) {
        NotionImportRun importRun = importRunRepository.findByIdForUpdate(importRunId)
                .orElseThrow(this::invalidImportRun);
        long storedPageCount = notionPageRepository.countByWorkspaceIdAndImportRunId(
                importRun.getWorkspaceId(),
                importRun.getId()
        );
        if (storedPageCount <= 0 || storedPageCount > Integer.MAX_VALUE
                || storedPageCount != importRun.getProcessedPageCount()) {
            throw invalidImportRun();
        }
        Instant publishedAt = currentTime();
        importRun.complete(publishedAt);
        importRunRepository.save(importRun);
        notionPageRepository.publish(
                importRun.getWorkspaceId(),
                importRun.getId(),
                publishedAt
        );
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private NotionImportException invalidImportRun() {
        return new NotionImportException(NotionImportErrorCode.INVALID_NOTION_IMPORT_RUN);
    }
}

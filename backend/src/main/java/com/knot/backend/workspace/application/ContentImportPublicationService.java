package com.knot.backend.workspace.application;

import com.knot.backend.workspace.domain.ContentImportErrorCode;
import com.knot.backend.workspace.domain.ContentImportException;
import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import com.knot.backend.workspace.domain.ImportedPageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentImportPublicationService {
    private final ContentImportRunRepository importRunRepository;
    private final ImportedPageRepository importedPageRepository;
    private final Clock clock;

    @Transactional
    public void publish(Long importRunId) {
        ContentImportRun importRun = importRunRepository.findByIdForUpdate(importRunId)
                .orElseThrow(this::invalidImportRun);
        long storedPageCount = importedPageRepository.countByWorkspaceIdAndImportRunId(
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
        importedPageRepository.publish(
                importRun.getWorkspaceId(),
                importRun.getId(),
                publishedAt
        );
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private ContentImportException invalidImportRun() {
        return new ContentImportException(ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN);
    }
}

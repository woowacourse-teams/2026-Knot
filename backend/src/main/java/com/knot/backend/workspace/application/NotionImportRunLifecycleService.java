package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ClaimedNotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.NotionImportStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotionImportRunLifecycleService {
    private final NotionImportRunRepository importRunRepository;
    private final Clock clock;

    @Transactional
    public Optional<ClaimedNotionImportRun> claimNext() {
        return importRunRepository.findFirstPendingForUpdate()
                .map(importRun -> {
                    importRun.start(currentTime());
                    importRunRepository.save(importRun);
                    return ClaimedNotionImportRun.from(importRun);
                });
    }

    @Transactional
    public boolean fail(Long importRunId) {
        NotionImportRun importRun = importRunRepository.findByIdForUpdate(importRunId)
                .orElse(null);
        if (importRun == null || !isActive(importRun)) {
            return false;
        }
        importRun.fail(currentTime());
        importRunRepository.save(importRun);
        return true;
    }

    private boolean isActive(NotionImportRun importRun) {
        return importRun.getStatus() == NotionImportStatus.PENDING
                || importRun.getStatus() == NotionImportStatus.RUNNING;
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }
}

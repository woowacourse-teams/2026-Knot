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
import org.springframework.transaction.annotation.Propagation;
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
                    NotionImportRun claimedImportRun = importRunRepository.save(importRun);
                    if (!importRunRepository.heartbeatIfRunning(claimedImportRun.getId())) {
                        throw new IllegalStateException("Notion Import 선점 heartbeat를 기록하지 못했습니다");
                    }
                    return ClaimedNotionImportRun.from(claimedImportRun);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean heartbeat(Long importRunId) {
        return importRunRepository.heartbeatIfRunning(importRunId);
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

package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ClaimedContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import com.knot.backend.workspace.domain.ContentImportStatus;
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
public class ContentImportRunLifecycleService {
    private final ContentImportRunRepository importRunRepository;
    private final Clock clock;

    @Transactional
    public Optional<ClaimedContentImportRun> claimNext() {
        return importRunRepository.findFirstPendingForUpdate()
                .map(importRun -> {
                    importRun.start(currentTime());
                    ContentImportRun claimedImportRun = importRunRepository.save(importRun);
                    if (!importRunRepository.heartbeatIfRunning(claimedImportRun.getId())) {
                        throw new IllegalStateException("Content Import 선점 heartbeat를 기록하지 못했습니다");
                    }
                    return ClaimedContentImportRun.from(claimedImportRun);
                });
    }

    @Transactional
    public boolean fail(Long importRunId) {
        ContentImportRun importRun = importRunRepository.findByIdForUpdate(importRunId)
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

    private boolean isActive(ContentImportRun importRun) {
        return importRun.getStatus() == ContentImportStatus.PENDING
                || importRun.getStatus() == ContentImportStatus.RUNNING;
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }
}

package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionImportRecoveryResult;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.NotionImportStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotionImportStaleRecoveryService {
    private final NotionImportRunRepository importRunRepository;
    private final Clock clock;

    @Transactional
    public NotionImportRecoveryResult recover(
            Duration pendingTimeout,
            Duration runningTimeout,
            int batchSize
    ) {
        validateArguments(
                pendingTimeout,
                runningTimeout,
                batchSize
        );
        Instant recoveredAt = currentTime();
        List<NotionImportRun> staleImportRuns = importRunRepository.findStaleForUpdate(
                recoveredAt.minus(pendingTimeout),
                recoveredAt.minus(runningTimeout),
                batchSize
        );
        int pendingCount = 0;
        int runningCount = 0;
        for (NotionImportRun importRun : staleImportRuns) {
            if (importRun.getStatus() == NotionImportStatus.PENDING) {
                pendingCount++;
            } else if (importRun.getStatus() == NotionImportStatus.RUNNING) {
                runningCount++;
            }
            importRun.fail(recoveredAt);
            importRunRepository.save(importRun);
        }
        return new NotionImportRecoveryResult(
                pendingCount,
                runningCount
        );
    }

    private void validateArguments(
            Duration pendingTimeout,
            Duration runningTimeout,
            int batchSize
    ) {
        if (pendingTimeout == null || pendingTimeout.isZero() || pendingTimeout.isNegative() || runningTimeout == null
                || runningTimeout.isZero() || runningTimeout.isNegative() || batchSize <= 0) {
            throw new IllegalArgumentException("Notion Import stale 복구 설정이 올바르지 않습니다");
        }
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }
}

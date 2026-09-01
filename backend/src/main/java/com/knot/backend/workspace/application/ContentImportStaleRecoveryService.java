package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ContentImportRecoveryResult;
import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentImportStaleRecoveryService {
    private final ContentImportRunRepository importRunRepository;

    @Transactional
    public ContentImportRecoveryResult recover(
            Duration runningTimeout,
            int batchSize
    ) {
        long runningTimeoutMillis = validateArguments(
                runningTimeout,
                batchSize
        );
        Instant recoveredAt = importRunRepository.currentDatabaseTime();
        List<ContentImportRun> staleImportRuns = importRunRepository.findStaleRunningForUpdate(
                runningTimeoutMillis,
                batchSize
        );
        for (ContentImportRun importRun : staleImportRuns) {
            importRun.fail(
                    laterOf(
                            recoveredAt,
                            importRun.getStartedAt()
                    )
            );
            importRunRepository.save(importRun);
        }
        return new ContentImportRecoveryResult(staleImportRuns.size());
    }

    private long validateArguments(
            Duration runningTimeout,
            int batchSize
    ) {
        if (runningTimeout == null || runningTimeout.isZero() || runningTimeout.isNegative() || batchSize <= 0) {
            throw new IllegalArgumentException("Content Import stale 복구 설정이 올바르지 않습니다");
        }
        try {
            long runningTimeoutMillis = runningTimeout.toMillis();
            if (runningTimeoutMillis < 1) {
                throw new IllegalArgumentException("Content Import stale 복구 설정이 올바르지 않습니다");
            }
            return runningTimeoutMillis;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Content Import stale 복구 설정이 올바르지 않습니다",
                    exception
            );
        }
    }

    private Instant laterOf(
            Instant first,
            Instant second
    ) {
        return first.isBefore(second) ? second : first;
    }
}

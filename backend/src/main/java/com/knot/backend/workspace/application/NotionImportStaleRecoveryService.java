package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionImportRecoveryResult;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotionImportStaleRecoveryService {
    private final NotionImportRunRepository importRunRepository;

    @Transactional
    public NotionImportRecoveryResult recover(
            Duration runningTimeout,
            int batchSize
    ) {
        validateArguments(
                runningTimeout,
                batchSize
        );
        Instant recoveredAt = importRunRepository.currentDatabaseTime();
        List<NotionImportRun> staleImportRuns = importRunRepository.findStaleRunningForUpdate(
                runningTimeout.toMillis(),
                batchSize
        );
        for (NotionImportRun importRun : staleImportRuns) {
            importRun.fail(
                    laterOf(
                            recoveredAt,
                            importRun.getStartedAt()
                    )
            );
            importRunRepository.save(importRun);
        }
        return new NotionImportRecoveryResult(staleImportRuns.size());
    }

    private void validateArguments(
            Duration runningTimeout,
            int batchSize
    ) {
        if (runningTimeout == null || runningTimeout.isZero() || runningTimeout.isNegative() || batchSize <= 0) {
            throw new IllegalArgumentException("Notion Import stale 복구 설정이 올바르지 않습니다");
        }
    }

    private Instant laterOf(
            Instant first,
            Instant second
    ) {
        return first.isBefore(second) ? second : first;
    }
}

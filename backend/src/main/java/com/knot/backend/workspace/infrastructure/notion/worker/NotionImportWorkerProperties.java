package com.knot.backend.workspace.infrastructure.notion.worker;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notion.import.worker")
public record NotionImportWorkerProperties(
        Duration pollDelay,
        Duration pendingStaleTimeout,
        Duration runningStaleTimeout,
        int recoveryBatchSize
) {

    public NotionImportWorkerProperties {
        if (!isPositive(pollDelay) || !isPositive(pendingStaleTimeout) || !isPositive(runningStaleTimeout)
                || recoveryBatchSize <= 0) {
            throw new IllegalArgumentException("Notion Import worker 설정이 올바르지 않습니다");
        }
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}

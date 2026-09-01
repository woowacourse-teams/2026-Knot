package com.knot.backend.workspace.infrastructure.notion.worker;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notion.import.worker")
public record NotionImportWorkerProperties(
        Duration pollDelay,
        Duration heartbeatInterval,
        Duration runningStaleTimeout,
        int recoveryBatchSize
) {

    public NotionImportWorkerProperties {
        long pollDelayMillis = toPositiveMillis(pollDelay);
        long heartbeatIntervalMillis = toPositiveMillis(heartbeatInterval);
        long runningStaleTimeoutMillis = toPositiveMillis(runningStaleTimeout);
        if (pollDelayMillis <= 0 || heartbeatIntervalMillis <= 0 || runningStaleTimeoutMillis <= 0
                || heartbeatIntervalMillis >= runningStaleTimeoutMillis || recoveryBatchSize <= 0) {
            throw new IllegalArgumentException("Notion Import worker 설정이 올바르지 않습니다");
        }
    }

    private static long toPositiveMillis(Duration duration) {
        if (duration == null) {
            return -1;
        }
        try {
            return duration.toMillis();
        } catch (ArithmeticException ignored) {
            return -1;
        }
    }
}

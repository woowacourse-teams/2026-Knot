package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.application.NotionImportFailureCategory;
import com.knot.backend.workspace.application.NotionImportWorkerObserver;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MicrometerNotionImportWorkerObserver implements NotionImportWorkerObserver {
    private static final String RUN_COUNTER = "knot.notion.import.runs";
    private static final String RECOVERY_COUNTER = "knot.notion.import.stale.recovered";
    private static final Logger log = LoggerFactory.getLogger(MicrometerNotionImportWorkerObserver.class);

    private final MeterRegistry meterRegistry;

    public MicrometerNotionImportWorkerObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void claimed(
            Long importRunId,
            Long workspaceId
    ) {
        incrementRunCounter("claimed");
        log.info(
                "Notion Import 작업을 선점했습니다: importRunId={}, workspaceId={}",
                importRunId,
                workspaceId
        );
    }

    @Override
    public void completed(
            Long importRunId,
            Long workspaceId,
            int pageCount
    ) {
        incrementRunCounter("completed");
        meterRegistry.summary("knot.notion.import.pages")
                .record(pageCount);
        log.info(
                "Notion Import 작업을 완료했습니다: importRunId={}, workspaceId={}, pageCount={}",
                importRunId,
                workspaceId,
                pageCount
        );
    }

    @Override
    public void failed(
            Long importRunId,
            Long workspaceId,
            NotionImportFailureCategory category
    ) {
        meterRegistry.counter(
                RUN_COUNTER,
                "outcome",
                "failed",
                "category",
                category.name()
        )
                .increment();
        log.warn(
                "Notion Import 작업을 실패 처리했습니다: importRunId={}, workspaceId={}, category={}",
                importRunId,
                workspaceId,
                category
        );
    }

    @Override
    public void staleRecovered(
            int pendingCount,
            int runningCount
    ) {
        meterRegistry.counter(
                RECOVERY_COUNTER,
                "previous_status",
                "PENDING"
        )
                .increment(pendingCount);
        meterRegistry.counter(
                RECOVERY_COUNTER,
                "previous_status",
                "RUNNING"
        )
                .increment(runningCount);
        if (pendingCount > 0 || runningCount > 0) {
            log.warn(
                    "오래된 Notion Import 작업을 회수했습니다: pendingCount={}, runningCount={}",
                    pendingCount,
                    runningCount
            );
        }
    }

    @Override
    public void pollingFailed() {
        incrementRunCounter("polling_failed");
        log.error("Notion Import polling에 실패했습니다");
    }

    private void incrementRunCounter(String outcome) {
        meterRegistry.counter(
                RUN_COUNTER,
                "outcome",
                outcome
        )
                .increment();
    }
}

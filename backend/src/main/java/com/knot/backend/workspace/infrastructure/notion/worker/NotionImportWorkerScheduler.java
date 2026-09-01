package com.knot.backend.workspace.infrastructure.notion.worker;

import com.knot.backend.workspace.application.NotionImportStaleRecoveryService;
import com.knot.backend.workspace.application.NotionImportWorker;
import com.knot.backend.workspace.application.NotionImportWorkerObserver;
import com.knot.backend.workspace.application.dto.result.NotionImportRecoveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class NotionImportWorkerScheduler {
    private final NotionImportStaleRecoveryService staleRecoveryService;
    private final NotionImportWorker worker;
    private final NotionImportWorkerObserver observer;
    private final NotionImportWorkerProperties properties;

    @Scheduled(fixedDelayString = "${notion.import.worker.poll-delay:PT1S}")
    public void poll() {
        try {
            NotionImportRecoveryResult recoveryResult = staleRecoveryService.recover(
                    properties.runningStaleTimeout(),
                    properties.recoveryBatchSize()
            );
            observer.staleRecovered(recoveryResult.runningCount());
            worker.processNext();
        } catch (RuntimeException ignored) {
            observer.pollingFailed();
        }
    }
}

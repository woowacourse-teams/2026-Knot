package com.knot.backend.workspace.infrastructure.notion.worker;

import com.knot.backend.workspace.application.ContentImportStaleRecoveryService;
import com.knot.backend.workspace.application.ContentImportWorker;
import com.knot.backend.workspace.application.ContentImportWorkerObserver;
import com.knot.backend.workspace.application.dto.result.ContentImportRecoveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class NotionImportWorkerScheduler {
    private final ContentImportStaleRecoveryService staleRecoveryService;
    private final ContentImportWorker worker;
    private final ContentImportWorkerObserver observer;
    private final NotionImportWorkerProperties properties;

    @Scheduled(fixedDelayString = "${notion.import.worker.poll-delay:PT1S}")
    public void poll() {
        try {
            ContentImportRecoveryResult recoveryResult = staleRecoveryService.recover(
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

package com.knot.backend.workspace.infrastructure.notion.worker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.NotionImportStaleRecoveryService;
import com.knot.backend.workspace.application.NotionImportWorker;
import com.knot.backend.workspace.application.NotionImportWorkerObserver;
import com.knot.backend.workspace.application.dto.result.NotionImportRecoveryResult;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotionImportWorkerSchedulerTest {
    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration RUNNING_TIMEOUT = Duration.ofHours(1);
    private static final int RECOVERY_BATCH_SIZE = 100;

    private final NotionImportStaleRecoveryService staleRecoveryService = mock(NotionImportStaleRecoveryService.class);
    private final NotionImportWorker worker = mock(NotionImportWorker.class);
    private final NotionImportWorkerObserver observer = mock(NotionImportWorkerObserver.class);
    private final NotionImportWorkerScheduler scheduler = new NotionImportWorkerScheduler(
            staleRecoveryService,
            worker,
            observer,
            new NotionImportWorkerProperties(
                    Duration.ofSeconds(1),
                    PENDING_TIMEOUT,
                    RUNNING_TIMEOUT,
                    RECOVERY_BATCH_SIZE
            )
    );

    @DisplayName("poll마다 stale Run을 먼저 회수하고 다음 PENDING Run을 처리한다")
    @Test
    void poll_success_recoversBeforeProcessing() {
        // given
        when(
                staleRecoveryService.recover(
                        PENDING_TIMEOUT,
                        RUNNING_TIMEOUT,
                        RECOVERY_BATCH_SIZE
                )
        ).thenReturn(
                new NotionImportRecoveryResult(
                        2,
                        1
                )
        );

        // when
        scheduler.poll();

        // then
        verify(observer).staleRecovered(
                2,
                1
        );
        verify(worker).processNext();
        verify(
                observer,
                never()
        ).pollingFailed();
    }

    @DisplayName("polling 경계가 실패하면 raw 예외 없이 실패 관측만 남긴다")
    @Test
    void poll_failure_observesWithoutProcessing() {
        // given
        when(
                staleRecoveryService.recover(
                        PENDING_TIMEOUT,
                        RUNNING_TIMEOUT,
                        RECOVERY_BATCH_SIZE
                )
        ).thenThrow(new IllegalStateException("database details"));

        // when
        scheduler.poll();

        // then
        verify(observer).pollingFailed();
        verify(
                worker,
                never()
        ).processNext();
    }
}

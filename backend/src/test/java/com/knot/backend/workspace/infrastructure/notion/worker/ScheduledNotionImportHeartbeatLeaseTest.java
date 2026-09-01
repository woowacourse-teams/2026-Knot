package com.knot.backend.workspace.infrastructure.notion.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.NotionImportHeartbeatLease;
import com.knot.backend.workspace.application.NotionImportRunLifecycleService;
import com.knot.backend.workspace.application.NotionImportWorkerObserver;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScheduledNotionImportHeartbeatLeaseTest {
    private static final Long IMPORT_RUN_ID = 1L;
    private static final Long WORKSPACE_ID = 2L;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final NotionImportRunLifecycleService lifecycleService = mock(NotionImportRunLifecycleService.class);
    private final NotionImportWorkerObserver observer = mock(NotionImportWorkerObserver.class);
    private final ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
    private final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
    private final ScheduledNotionImportHeartbeatLease heartbeatLease = new ScheduledNotionImportHeartbeatLease(
            lifecycleService,
            observer,
            new NotionImportWorkerProperties(
                    Duration.ofSeconds(1),
                    HEARTBEAT_INTERVAL,
                    Duration.ofHours(1),
                    100
            ),
            scheduledExecutorService
    );

    @DisplayName("blocking 수집과 별개인 전용 scheduler에서 RUNNING heartbeat를 갱신하고 종료 시 취소한다")
    @Test
    void lease_success_renewsAndCancels() {
        // given
        ArgumentCaptor<Runnable> heartbeatTask = allowScheduling();
        when(lifecycleService.heartbeat(IMPORT_RUN_ID)).thenReturn(true);

        // when
        NotionImportHeartbeatLease.Handle handle = heartbeatLease.start(
                IMPORT_RUN_ID,
                WORKSPACE_ID
        );
        heartbeatTask.getValue()
                .run();
        handle.close();

        // then
        verify(lifecycleService).heartbeat(IMPORT_RUN_ID);
        verify(scheduledFuture).cancel(false);
        assertThat(handle.isActive()).isFalse();
    }

    @DisplayName("terminal 전이로 heartbeat 갱신 대상이 사라지면 lease를 비활성화한다")
    @Test
    void lease_success_stopsWhenRunIsNotRunning() {
        // given
        ArgumentCaptor<Runnable> heartbeatTask = allowScheduling();
        when(lifecycleService.heartbeat(IMPORT_RUN_ID)).thenReturn(false);
        NotionImportHeartbeatLease.Handle handle = heartbeatLease.start(
                IMPORT_RUN_ID,
                WORKSPACE_ID
        );

        // when
        heartbeatTask.getValue()
                .run();

        // then
        assertThat(handle.isActive()).isFalse();
        handle.close();
    }

    @DisplayName("heartbeat 저장 실패는 raw 오류 없이 관측하고 다음 주기에 재시도할 수 있다")
    @Test
    void lease_failure_observesAndRemainsActive() {
        // given
        ArgumentCaptor<Runnable> heartbeatTask = allowScheduling();
        when(lifecycleService.heartbeat(IMPORT_RUN_ID)).thenThrow(new IllegalStateException("database details"))
                .thenReturn(true);
        NotionImportHeartbeatLease.Handle handle = heartbeatLease.start(
                IMPORT_RUN_ID,
                WORKSPACE_ID
        );

        // when
        heartbeatTask.getValue()
                .run();
        heartbeatTask.getValue()
                .run();

        // then
        verify(observer).heartbeatFailed(
                IMPORT_RUN_ID,
                WORKSPACE_ID
        );
        verify(
                lifecycleService,
                times(2)
        ).heartbeat(IMPORT_RUN_ID);
        assertThat(handle.isActive()).isTrue();
        handle.close();
    }

    @DisplayName("heartbeat 예약 자체가 실패하면 관측하고 lease 시작을 거부한다")
    @Test
    void lease_failure_scheduling() {
        // given
        when(
                scheduledExecutorService.scheduleWithFixedDelay(
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        anyLong(),
                        anyLong(),
                        eq(TimeUnit.MILLISECONDS)
                )
        ).thenThrow(new IllegalStateException("scheduler details"));

        // when
        Throwable thrown = catchThrowable(
                () -> heartbeatLease.start(
                        IMPORT_RUN_ID,
                        WORKSPACE_ID
                )
        );

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(observer).heartbeatFailed(
                IMPORT_RUN_ID,
                WORKSPACE_ID
        );
    }

    private ArgumentCaptor<Runnable> allowScheduling() {
        ArgumentCaptor<Runnable> heartbeatTask = ArgumentCaptor.forClass(Runnable.class);
        doReturn(scheduledFuture).when(scheduledExecutorService)
                .scheduleWithFixedDelay(
                        heartbeatTask.capture(),
                        eq(HEARTBEAT_INTERVAL.toMillis()),
                        eq(HEARTBEAT_INTERVAL.toMillis()),
                        eq(TimeUnit.MILLISECONDS)
                );
        return heartbeatTask;
    }
}

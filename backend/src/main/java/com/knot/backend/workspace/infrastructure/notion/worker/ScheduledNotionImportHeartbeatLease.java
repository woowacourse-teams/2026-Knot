package com.knot.backend.workspace.infrastructure.notion.worker;

import com.knot.backend.workspace.application.ContentImportHeartbeatLease;
import com.knot.backend.workspace.application.ContentImportRunLifecycleService;
import com.knot.backend.workspace.application.ContentImportWorkerObserver;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ScheduledNotionImportHeartbeatLease implements ContentImportHeartbeatLease {
    private final ContentImportRunLifecycleService lifecycleService;
    private final ContentImportWorkerObserver observer;
    private final NotionImportWorkerProperties properties;
    private final ScheduledExecutorService scheduledExecutorService;

    @Override
    public Handle start(
            Long importRunId,
            Long workspaceId
    ) {
        AtomicBoolean active = new AtomicBoolean(true);
        ScheduledFuture<?> scheduledFuture;
        try {
            long heartbeatIntervalMillis = properties.heartbeatInterval()
                    .toMillis();
            scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                    () -> renew(
                            importRunId,
                            workspaceId,
                            active
                    ),
                    heartbeatIntervalMillis,
                    heartbeatIntervalMillis,
                    TimeUnit.MILLISECONDS
            );
        } catch (RuntimeException ignored) {
            observer.heartbeatFailed(
                    importRunId,
                    workspaceId
            );
            throw ignored;
        }
        if (scheduledFuture == null) {
            observer.heartbeatFailed(
                    importRunId,
                    workspaceId
            );
            throw new IllegalStateException("Notion Import heartbeat를 예약하지 못했습니다");
        }
        return new ScheduledHandle(
                active,
                scheduledFuture
        );
    }

    private void renew(
            Long importRunId,
            Long workspaceId,
            AtomicBoolean active
    ) {
        if (!active.get()) {
            return;
        }
        try {
            if (!lifecycleService.heartbeat(importRunId)) {
                active.set(false);
            }
        } catch (RuntimeException ignored) {
            observer.heartbeatFailed(
                    importRunId,
                    workspaceId
            );
        }
    }

    private static final class ScheduledHandle implements Handle {
        private final AtomicBoolean active;
        private final ScheduledFuture<?> scheduledFuture;

        private ScheduledHandle(
                AtomicBoolean active,
                ScheduledFuture<?> scheduledFuture
        ) {
            this.active = active;
            this.scheduledFuture = scheduledFuture;
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void close() {
            active.set(false);
            scheduledFuture.cancel(false);
        }
    }
}

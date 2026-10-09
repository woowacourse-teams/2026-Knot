package com.knot.backend.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;

import com.knot.backend.document.application.DocumentGenerationWorker;
import com.knot.backend.document.application.DocumentGenerationRecoveryService;
import com.knot.backend.document.domain.DocumentGenerationCandidate;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.global.config.DocumentGenerationWorkerProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class DocumentGenerationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    @DisplayName("스케줄러 제출이 거절되면 Job을 확보하거나 상태를 바꾸지 않는다")
    void dispatchQueued_failure_rejectedSubmission() {
        // given
        DocumentGenerationJobRepository jobs = mock(DocumentGenerationJobRepository.class);
        DocumentGenerationWorker worker = mock(DocumentGenerationWorker.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        when(
                jobs.findReadyCandidates(
                        any(),
                        anyInt()
                )
        ).thenReturn(
                List.of(
                        new DocumentGenerationCandidate(
                                1,
                                2,
                                1
                        )
                )
        );
        doThrow(new TaskRejectedException("full")).when(executor)
                .execute(any(Runnable.class));
        // when
        scheduler(
                jobs,
                worker,
                mock(DocumentGenerationRecoveryService.class),
                executor
        ).dispatchQueued();
        // then
        verify(
                worker,
                never()
        ).execute(
                1,
                2
        );
        verify(
                jobs,
                never()
        ).flush();
    }

    @Test
    @DisplayName("실행 여력이 있으면 스케줄러가 발견한 Job을 실행기로 넘긴다")
    void dispatchQueued_success_submitsCandidate() throws Exception {
        // given
        DocumentGenerationJobRepository jobs = mock(DocumentGenerationJobRepository.class);
        DocumentGenerationWorker worker = mock(DocumentGenerationWorker.class);
        when(
                jobs.findReadyCandidates(
                        any(),
                        anyInt()
                )
        ).thenReturn(
                List.of(
                        new DocumentGenerationCandidate(
                                1,
                                2,
                                1
                        )
                )
        );
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();
        CountDownLatch executed = new CountDownLatch(1);
        doAnswer(invocation -> {
            executed.countDown();
            return null;
        }).when(worker)
                .execute(
                        1,
                        2
                );
        try {
            // when
            scheduler(
                    jobs,
                    worker,
                    mock(DocumentGenerationRecoveryService.class),
                    executor
            ).dispatchQueued();
            // then
            assertThat(
                    executed.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
            verify(worker).execute(
                    1,
                    2
            );
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("앞선 작업 회수가 실패해도 다음 작업의 복구를 계속한다")
    void recoverExpired_success_continuesAfterFailure() {
        // given
        DocumentGenerationJobRepository jobs = mock(DocumentGenerationJobRepository.class);
        DocumentGenerationRecoveryService recovery = mock(DocumentGenerationRecoveryService.class);
        when(
                jobs.findExpiredCandidates(
                        any(),
                        anyInt()
                )
        ).thenReturn(
                List.of(
                        new DocumentGenerationCandidate(
                                1,
                                2,
                                1
                        ),
                        new DocumentGenerationCandidate(
                                1,
                                3,
                                2
                        )
                )
        );
        doThrow(new IllegalStateException("test")).when(recovery)
                .recoverExpired(
                        1,
                        2,
                        1
                );
        // when
        scheduler(
                jobs,
                mock(DocumentGenerationWorker.class),
                recovery,
                mock(ThreadPoolTaskExecutor.class)
        ).recoverExpired();
        // then
        verify(recovery).recoverExpired(
                1,
                3,
                2
        );
    }

    private DocumentGenerationScheduler scheduler(
            DocumentGenerationJobRepository jobs,
            DocumentGenerationWorker worker,
            DocumentGenerationRecoveryService recovery,
            ThreadPoolTaskExecutor executor
    ) {
        return new DocumentGenerationScheduler(
                jobs,
                worker,
                recovery,
                new DocumentGenerationWorkerProperties(),
                executor,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
    }
}

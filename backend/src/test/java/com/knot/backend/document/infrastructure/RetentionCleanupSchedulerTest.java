package com.knot.backend.document.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.application.DocumentRetentionCleanupService;
import com.knot.backend.document.domain.DocumentRetentionCandidate;
import com.knot.backend.document.domain.DocumentRetentionRepository;
import com.knot.backend.global.config.RetentionCleanupProperties;
import com.knot.backend.recording.application.RecordingAudioDeletionWorker;
import com.knot.backend.recording.application.RecordingAudioRetentionService;
import com.knot.backend.recording.domain.RecordingAudioDeletionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class RetentionCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    private DocumentRetentionRepository documents;
    private DocumentRetentionCleanupService cleanup;
    private RecordingAudioDeletionRepository audio;
    private RecordingAudioRetentionService audioRetention;
    private RecordingAudioDeletionWorker worker;
    private ThreadPoolTaskExecutor executor;
    private RetentionCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        documents = mock(DocumentRetentionRepository.class);
        cleanup = mock(DocumentRetentionCleanupService.class);
        audio = mock(RecordingAudioDeletionRepository.class);
        audioRetention = mock(RecordingAudioRetentionService.class);
        worker = mock(RecordingAudioDeletionWorker.class);
        executor = mock(ThreadPoolTaskExecutor.class);
        scheduler = new RetentionCleanupScheduler(
                documents,
                cleanup,
                audio,
                audioRetention,
                worker,
                new RetentionCleanupProperties(),
                executor,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    @DisplayName("개별 후보 정리가 실패해도 다음 녹음과 오디오 접수를 계속한다")
    void cleanupExpiredData_failureContinuesNextCandidate() {
        when(
                documents.findCandidates(
                        NOW,
                        20
                )
        ).thenReturn(
                List.of(
                        new DocumentRetentionCandidate(
                                1,
                                2,
                                3
                        ),
                        new DocumentRetentionCandidate(
                                1,
                                4,
                                5
                        )
                )
        );
        when(
                audio.findRetentionCandidates(
                        NOW.minus(Duration.ofDays(30)),
                        20
                )
        ).thenReturn(List.of(10L));
        doThrow(new RuntimeException("DB 오류")).when(cleanup)
                .cleanup(
                        1,
                        2,
                        3
                );

        scheduler.cleanupExpiredData();

        verify(cleanup).cleanup(
                1,
                4,
                5
        );
        verify(audioRetention).schedule(10);
    }

    @Test
    @DisplayName("실행 슬롯에 제출한 후에만 삭제 Worker를 호출한다")
    void dispatchAudioDeletion_successSubmittedBeforeClaim() {
        when(
                audio.findReadyTasks(
                        NOW,
                        20
                )
        ).thenReturn(List.of(10L));

        scheduler.dispatchAudioDeletion();

        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(runnable.capture());
        verifyNoInteractions(worker);
        runnable.getValue()
                .run();
        verify(worker).execute(10);
    }

    @Test
    @DisplayName("실행 슬롯이 없으면 작업 상태를 바꾸거나 Worker를 실행하지 않는다")
    void dispatchAudioDeletion_failureRejectedTaskUnchanged() {
        when(
                audio.findReadyTasks(
                        NOW,
                        20
                )
        ).thenReturn(
                List.of(
                        10L,
                        11L
                )
        );
        doThrow(new TaskRejectedException("실행 슬롯 없음")).when(executor)
                .execute(any(Runnable.class));

        scheduler.dispatchAudioDeletion();

        verifyNoInteractions(worker);
        verify(
                audio,
                never()
        ).findTaskForUpdate(10);
    }
}

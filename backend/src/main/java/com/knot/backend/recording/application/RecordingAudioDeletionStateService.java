package com.knot.backend.recording.application;

import com.knot.backend.global.config.RetentionCleanupProperties;
import com.knot.backend.recording.application.dto.result.RecordingAudioDeletionExecution;
import com.knot.backend.recording.domain.RecordingAudioDeletionRepository;
import com.knot.backend.recording.domain.RecordingAudioDeletionStatus;
import com.knot.backend.recording.domain.RecordingAudioDeletionTask;
import com.knot.backend.recording.domain.RecordingAudioUpload;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordingAudioDeletionStateService {

    private final RecordingAudioDeletionRepository deletions;
    private final RetentionCleanupProperties properties;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<RecordingAudioDeletionExecution> claim(long taskId) {
        Optional<RecordingAudioDeletionTask> task = lockUploadAndTask(taskId);
        if (task.isEmpty()) {
            return Optional.empty();
        }
        Instant now = now();
        RecordingAudioDeletionTask stored = task.orElseThrow();
        if (!stored.isReadyAt(now)) {
            return Optional.empty();
        }
        stored.claim(
                now,
                now.plus(properties.getExecutionLease())
        );
        return Optional.of(
                new RecordingAudioDeletionExecution(
                        taskId,
                        stored.getAttemptCount(),
                        stored.getStorageKey()
                )
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void succeed(
            long taskId,
            int attempt
    ) {
        Optional<RecordingAudioDeletionTask> task = lockUploadAndTask(taskId);
        if (task.isEmpty()) {
            return;
        }
        RecordingAudioDeletionTask stored = task.orElseThrow();
        Instant now = now();
        stored.recordSuccess(
                attempt,
                now
        );
        if (stored.getStatus() == RecordingAudioDeletionStatus.SUCCEEDED) {
            deletions.findUploadForUpdate(stored.getUploadId())
                    .orElseThrow()
                    .recordDeleted(now);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void fail(
            long taskId,
            int attempt
    ) {
        Optional<RecordingAudioDeletionTask> task = lockUploadAndTask(taskId);
        if (task.isEmpty()) {
            return;
        }
        RecordingAudioDeletionTask stored = task.orElseThrow();
        Instant now = now();
        stored.recordFailure(
                attempt,
                now,
                now.plus(retryBackoff(attempt)),
                "AUDIO_STORAGE_UNAVAILABLE"
        );
    }

    private Duration retryBackoff(int attempt) {
        return Duration.ofSeconds(
                Math.min(
                        3600L,
                        60L << Math.min(
                                Math.max(
                                        attempt - 1,
                                        0
                                ),
                                6
                        )
                )
        );
    }

    private Optional<RecordingAudioDeletionTask> lockUploadAndTask(long taskId) {
        Optional<RecordingAudioDeletionTask> task = deletions.findTask(taskId);
        if (task.isEmpty()) {
            return Optional.empty();
        }
        Optional<RecordingAudioUpload> upload = deletions.findUploadForUpdate(
                task.orElseThrow()
                        .getUploadId()
        );
        if (upload.isEmpty()) {
            return Optional.empty();
        }
        return deletions.findTaskForUpdate(taskId);
    }

    private Instant now() {
        return clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
    }
}

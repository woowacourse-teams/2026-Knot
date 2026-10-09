package com.knot.backend.recording.application;

import com.knot.backend.global.config.RetentionCleanupProperties;
import com.knot.backend.recording.domain.RecordingAudioDeletionRepository;
import com.knot.backend.recording.domain.RecordingAudioDeletionTask;
import com.knot.backend.recording.domain.RecordingAudioUpload;
import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordingAudioRetentionService {

    private final RecordingAudioDeletionRepository deletions;
    private final RetentionCleanupProperties properties;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void schedule(long uploadId) {
        Optional<RecordingAudioUpload> upload = deletions.findUploadForUpdate(uploadId);
        if (upload.isEmpty()) {
            return;
        }
        RecordingAudioUpload stored = upload.orElseThrow();
        Instant now = now();
        if (stored.isRetentionExpired(now) || deletions.hasNoContentResult(stored.getRecordingId())) {
            scheduleLocked(
                    stored,
                    now
            );
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleNoContent(long recordingId) {
        if (!deletions.hasNoContentResult(recordingId)) {
            return;
        }
        deletions.findUploadByRecordingForUpdate(recordingId)
                .ifPresent(
                        upload -> scheduleLocked(
                                upload,
                                now()
                        )
                );
    }

    private void scheduleLocked(
            RecordingAudioUpload upload,
            Instant now
    ) {
        if (upload.getStatus() != RecordingAudioUploadStatus.COMPLETED || upload.getDeletedAt() != null
                || deletions.hasTaskForUpload(upload.getId())) {
            return;
        }
        deletions.saveAndFlush(
                RecordingAudioDeletionTask.request(
                        upload.getId(),
                        upload.getStorageKey(),
                        now,
                        confirmationAfter(
                                upload,
                                now
                        )
                )
        );
    }

    private Instant confirmationAfter(
            RecordingAudioUpload upload,
            Instant now
    ) {
        Instant expiry = upload.getLastUploadUrlExpiresAt();
        if (expiry == null) {
            expiry = upload.getCompletedAt()
                    .plus(properties.getLegacyUrlValidity());
        }
        Instant confirmation = expiry.plus(properties.getUploadQuiescence());
        if (confirmation.isBefore(now)) {
            return now;
        }
        return confirmation;
    }

    private Instant now() {
        return clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
    }
}

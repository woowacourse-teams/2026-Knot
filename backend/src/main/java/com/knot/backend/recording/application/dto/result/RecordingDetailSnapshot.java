package com.knot.backend.recording.application.dto.result;

import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import com.knot.backend.recording.domain.RecordingEndReason;
import com.knot.backend.recording.domain.RecordingStatus;
import java.time.Instant;

public record RecordingDetailSnapshot(
        long recordingId,
        long memberId,
        RecordingStatus status,
        Instant startedAt,
        Instant currentIntervalStartedAt,
        long accumulatedRecordingMillis,
        Instant lastSeenAt,
        Instant endedAt,
        RecordingEndReason endReason,
        Long uploadId,
        RecordingAudioUploadStatus audioUploadStatus,
        Instant completedAt
) {
}

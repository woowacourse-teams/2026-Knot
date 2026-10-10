package com.knot.backend.recording.application.dto.result;

import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import com.knot.backend.recording.domain.RecordingEndReason;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingStatus;
import java.time.Duration;
import java.time.Instant;

public record RecordingDetailResult(
        long recordingId,
        RecordingStatus status,
        Instant startedAt,
        long elapsedMillis,
        Instant endedAt,
        RecordingEndReason endReason,
        Instant expiresAt,
        Instant serverNow,
        RecordingAudioUploadStatus audioUploadStatus,
        Long uploadId,
        Instant completedAt,
        long maxDurationMillis
) {

    public static RecordingDetailResult of(
            RecordingDetailSnapshot snapshot,
            Instant serverNow
    ) {
        return new RecordingDetailResult(
                snapshot.recordingId(),
                snapshot.status(),
                snapshot.startedAt(),
                elapsedMillis(
                        snapshot,
                        serverNow
                ),
                snapshot.endedAt(),
                snapshot.endReason(),
                expiresAt(snapshot),
                serverNow,
                snapshot.audioUploadStatus(),
                snapshot.uploadId(),
                snapshot.completedAt(),
                RecordingSession.MAX_RECORDING_DURATION.toMillis()
        );
    }

    // 다른 서버의 시계가 조금 늦어도 조회가 실패하지 않도록 진행 구간을 음수로 세지 않는다
    private static long elapsedMillis(
            RecordingDetailSnapshot snapshot,
            Instant serverNow
    ) {
        if (snapshot.status() != RecordingStatus.RECORDING) {
            return snapshot.accumulatedRecordingMillis();
        }
        long currentIntervalMillis = Duration.between(
                snapshot.currentIntervalStartedAt(),
                serverNow
        )
                .toMillis();
        return snapshot.accumulatedRecordingMillis() + Math.max(
                0L,
                currentIntervalMillis
        );
    }

    private static Instant expiresAt(RecordingDetailSnapshot snapshot) {
        if (!RecordingStatus.ACTIVE_STATUSES.contains(snapshot.status())) {
            return null;
        }
        return RecordingSession.connectionExpiresAt(snapshot.lastSeenAt());
    }
}

package com.knot.backend.recording.application.dto.result;

import com.knot.backend.recording.domain.RecordingEndReason;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingStatus;
import java.time.Instant;

public record RecordingHeartbeatResult(
        long recordingId,
        RecordingStatus status,
        long elapsedMillis,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant endedAt,
        RecordingEndReason endReason,
        Instant serverNow
) {

    public static RecordingHeartbeatResult of(
            RecordingSession session,
            Instant serverNow
    ) {
        return new RecordingHeartbeatResult(
                session.getId(),
                session.getStatus(),
                session.getRecordingDurationMillis(serverNow),
                session.getLastSeenAt(),
                session.getExpiresAt(),
                session.getEndedAt(),
                session.getEndReason(),
                serverNow
        );
    }
}

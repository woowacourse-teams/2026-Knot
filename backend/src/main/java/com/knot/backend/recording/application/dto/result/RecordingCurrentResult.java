package com.knot.backend.recording.application.dto.result;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingStatus;
import java.time.Instant;

public record RecordingCurrentResult(
        long recordingId,
        RecordingStatus status,
        Instant startedAt,
        long elapsedMillis
) {

    public static RecordingCurrentResult of(
            RecordingSession session,
            Instant now
    ) {
        return new RecordingCurrentResult(
                session.getId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getRecordingDurationMillis(now)
        );
    }
}

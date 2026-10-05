package com.knot.backend.recording.application.dto.result;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingStatus;
import java.time.Instant;

public record RecordingPauseResult(
        long recordingId,
        RecordingStatus status,
        Instant pausedAt,
        long elapsedMillis
) {

    public static RecordingPauseResult from(RecordingSession session) {
        return new RecordingPauseResult(
                session.getId(),
                session.getStatus(),
                session.getPausedAt(),
                session.getAccumulatedRecordingMillis()
        );
    }
}

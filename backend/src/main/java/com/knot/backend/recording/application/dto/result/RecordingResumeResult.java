package com.knot.backend.recording.application.dto.result;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingStatus;
import java.time.Instant;

public record RecordingResumeResult(
        long recordingId,
        RecordingStatus status,
        Instant resumedAt,
        long elapsedMillis
) {

    public static RecordingResumeResult from(RecordingSession session) {
        return new RecordingResumeResult(
                session.getId(),
                session.getStatus(),
                session.getResumedAt(),
                session.getAccumulatedRecordingMillis()
        );
    }
}

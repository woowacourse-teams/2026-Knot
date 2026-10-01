package com.knot.backend.recording.application.dto.result;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingStatus;
import java.time.Instant;

public record RecordingStartResult(
        long recordingId,
        RecordingStatus status,
        Instant startedAt,
        boolean created
) {

    public static RecordingStartResult from(
            RecordingSession session,
            boolean created
    ) {
        return new RecordingStartResult(
                session.getId(),
                session.getStatus(),
                session.getStartedAt(),
                created
        );
    }
}

package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.command.RecordingControlCommand;
import com.knot.backend.recording.application.dto.result.RecordingPauseResult;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordingPauseTransaction {
    private final RecordingControlledSessionLoader controlledSessionLoader;
    private final RecordingSessionRepository recordingSessionRepository;
    private final Clock clock;

    @Transactional
    public RecordingPauseResult pause(
            long workspaceId,
            long memberId,
            long recordingId,
            RecordingControlCommand command
    ) {
        RecordingSession session = controlledSessionLoader.loadForUpdate(
                workspaceId,
                memberId,
                recordingId,
                command
        );
        Instant now = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
        if (!session.expireIfDisconnected(now)) {
            session.pause(now);
        }
        return RecordingPauseResult.from(recordingSessionRepository.save(session));
    }
}

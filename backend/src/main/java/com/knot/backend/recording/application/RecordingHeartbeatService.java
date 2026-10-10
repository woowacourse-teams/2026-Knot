package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.command.RecordingControlCommand;
import com.knot.backend.recording.application.dto.result.RecordingHeartbeatResult;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordingHeartbeatService {
    private final RecordingControlledSessionLoader controlledSessionLoader;
    private final RecordingSessionRepository recordingSessionRepository;
    private final Clock clock;

    @Transactional
    public RecordingHeartbeatResult heartbeat(
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
            session.recordHeartbeat(now);
        }
        return RecordingHeartbeatResult.of(
                recordingSessionRepository.save(session),
                now
        );
    }
}

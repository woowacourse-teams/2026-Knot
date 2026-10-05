package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.command.RecordingControlCommand;
import com.knot.backend.recording.application.dto.result.RecordingResumeResult;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordingResumeService {
    private final RecordingWorkspaceAccessValidator workspaceAccessValidator;
    private final RecordingSessionRepository recordingSessionRepository;
    private final RecordingControlTokenHasher controlTokenHasher;
    private final Clock clock;

    @Transactional
    public RecordingResumeResult resume(
            long workspaceId,
            long memberId,
            long recordingId,
            RecordingControlCommand command
    ) {
        workspaceAccessValidator.validateAndLock(
                workspaceId,
                memberId
        );
        if (recordingId <= 0) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
        RecordingSession session = recordingSessionRepository.findByIdForUpdate(recordingId)
                .orElseThrow(() -> new RecordingException(RecordingErrorCode.RECORDING_NOT_FOUND));
        session.validateControlledBy(
                workspaceId,
                memberId
        );
        session.validateControlProof(
                command.tabId(),
                controlTokenHasher.hash(command.controlToken())
        );
        session.resume(
                clock.instant()
                        .truncatedTo(ChronoUnit.MICROS)
        );
        return RecordingResumeResult.from(recordingSessionRepository.save(session));
    }
}

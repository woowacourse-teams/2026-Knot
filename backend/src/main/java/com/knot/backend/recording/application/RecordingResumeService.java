package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.command.RecordingControlCommand;
import com.knot.backend.recording.application.dto.result.RecordingResumeResult;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import com.knot.backend.recording.domain.RecordingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordingResumeService {
    private final RecordingResumeTransaction resumeTransaction;

    public RecordingResumeResult resume(
            long workspaceId,
            long memberId,
            long recordingId,
            RecordingControlCommand command
    ) {
        RecordingResumeResult result = resumeTransaction.resume(
                workspaceId,
                memberId,
                recordingId,
                command
        );
        if (result.status() != RecordingStatus.RECORDING) {
            throw new RecordingException(RecordingErrorCode.RECORDING_ALREADY_ENDED);
        }
        return result;
    }
}

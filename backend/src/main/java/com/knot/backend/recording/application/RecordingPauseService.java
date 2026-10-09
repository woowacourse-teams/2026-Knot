package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.command.RecordingControlCommand;
import com.knot.backend.recording.application.dto.result.RecordingPauseResult;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import com.knot.backend.recording.domain.RecordingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordingPauseService {
    private final RecordingPauseTransaction pauseTransaction;

    // 연결 만료로 종료한 결과는 커밋한 뒤 거절해야 종료 기록이 롤백되지 않는다
    public RecordingPauseResult pause(
            long workspaceId,
            long memberId,
            long recordingId,
            RecordingControlCommand command
    ) {
        RecordingPauseResult result = pauseTransaction.pause(
                workspaceId,
                memberId,
                recordingId,
                command
        );
        if (result.status() != RecordingStatus.PAUSED) {
            throw new RecordingException(RecordingErrorCode.RECORDING_ALREADY_ENDED);
        }
        return result;
    }
}

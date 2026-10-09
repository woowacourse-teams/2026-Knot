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

    // 연결 만료로 종료한 결과는 커밋한 뒤 거절해야 종료 기록이 롤백되지 않는다
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

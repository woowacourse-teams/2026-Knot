package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.result.RecordingDetailResult;
import com.knot.backend.recording.application.dto.result.RecordingDetailSnapshot;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordingDetailService {
    private final RecordingWorkspaceAccessValidator workspaceAccessValidator;
    private final RecordingDetailQuery recordingDetailQuery;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RecordingDetailResult find(
            long workspaceId,
            long memberId,
            long recordingId
    ) {
        workspaceAccessValidator.validate(
                workspaceId,
                memberId
        );
        if (recordingId <= 0) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
        RecordingDetailSnapshot snapshot = recordingDetailQuery.find(
                workspaceId,
                recordingId
        )
                .orElseThrow(() -> new RecordingException(RecordingErrorCode.RECORDING_NOT_FOUND));
        if (snapshot.memberId() != memberId) {
            throw new RecordingException(RecordingErrorCode.RECORDING_CONTROL_DENIED);
        }
        // 녹음을 읽은 뒤 시각을 잡아야 동시에 커밋된 재개보다 이른 시각으로 경과 시간을 계산하지 않는다
        return RecordingDetailResult.of(
                snapshot,
                clock.instant()
                        .truncatedTo(ChronoUnit.MICROS)
        );
    }
}

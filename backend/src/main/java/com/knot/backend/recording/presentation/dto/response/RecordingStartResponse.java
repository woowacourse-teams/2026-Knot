package com.knot.backend.recording.presentation.dto.response;

import com.knot.backend.recording.application.dto.result.RecordingStartResult;
import com.knot.backend.recording.domain.RecordingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RecordingStartResponse(
        @Schema(description = "녹음 세션 ID") long recordingId,
        @Schema(description = "새 요청은 RECORDING, 재시도는 기존 세션의 현재 상태") RecordingStatus status,
        @Schema(description = "서버가 확정한 시작 시각") Instant startedAt
) {

    public static RecordingStartResponse from(RecordingStartResult result) {
        return new RecordingStartResponse(
                result.recordingId(),
                result.status(),
                result.startedAt()
        );
    }
}

package com.knot.backend.recording.presentation.dto.response;

import com.knot.backend.recording.application.dto.result.RecordingCurrentResult;
import com.knot.backend.recording.domain.RecordingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RecordingCurrentResponse(
        @Schema(description = "녹음 세션 ID") long recordingId,
        @Schema(description = "RECORDING 또는 PAUSED") RecordingStatus status,
        @Schema(description = "서버가 확정한 녹음 시작 시각") Instant startedAt,
        @Schema(description = "조회 시점까지의 누적 녹음 시간(ms). 일시정지 구간은 제외") long elapsedMillis
) {

    public static RecordingCurrentResponse from(RecordingCurrentResult result) {
        return new RecordingCurrentResponse(
                result.recordingId(),
                result.status(),
                result.startedAt(),
                result.elapsedMillis()
        );
    }
}

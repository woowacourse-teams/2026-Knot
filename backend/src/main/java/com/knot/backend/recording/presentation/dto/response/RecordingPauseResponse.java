package com.knot.backend.recording.presentation.dto.response;

import com.knot.backend.recording.application.dto.result.RecordingPauseResult;
import com.knot.backend.recording.domain.RecordingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RecordingPauseResponse(
        @Schema(description = "녹음 세션 ID") long recordingId,
        @Schema(description = "항상 PAUSED") RecordingStatus status,
        @Schema(description = "서버가 확정한 일시정지 시각. 반복 요청에도 바뀌지 않는다") Instant pausedAt,
        @Schema(description = "일시정지 시점까지의 누적 녹음 시간(ms). 일시정지 구간은 제외") long elapsedMillis
) {

    public static RecordingPauseResponse from(RecordingPauseResult result) {
        return new RecordingPauseResponse(
                result.recordingId(),
                result.status(),
                result.pausedAt(),
                result.elapsedMillis()
        );
    }
}

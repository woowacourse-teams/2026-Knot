package com.knot.backend.recording.presentation.dto.response;

import com.knot.backend.recording.application.dto.result.RecordingResumeResult;
import com.knot.backend.recording.domain.RecordingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RecordingResumeResponse(
        @Schema(description = "녹음 세션 ID") long recordingId,
        @Schema(description = "항상 RECORDING") RecordingStatus status,
        @Schema(description = "서버가 확정한 재개 시각. 반복 요청에도 바뀌지 않는다") Instant resumedAt,
        @Schema(description = "재개 직전까지의 누적 녹음 시간(ms). 일시정지 구간은 제외") long elapsedMillis
) {

    public static RecordingResumeResponse from(RecordingResumeResult result) {
        return new RecordingResumeResponse(
                result.recordingId(),
                result.status(),
                result.resumedAt(),
                result.elapsedMillis()
        );
    }
}

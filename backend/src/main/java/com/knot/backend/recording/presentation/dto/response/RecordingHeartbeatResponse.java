package com.knot.backend.recording.presentation.dto.response;

import com.knot.backend.recording.application.dto.result.RecordingHeartbeatResult;
import com.knot.backend.recording.domain.RecordingEndReason;
import com.knot.backend.recording.domain.RecordingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RecordingHeartbeatResponse(
        @Schema(description = "녹음 세션 ID") long recordingId,
        @Schema(description = "녹음 상태") RecordingStatus status,
        @Schema(description = "일시정지를 뺀 서버 기준 녹음 시간") long elapsedMillis,
        @Schema(description = "서버가 받은 마지막 유효 신호 시각") Instant lastSeenAt,
        @Schema(description = "신호가 없으면 연결 만료로 종료되는 시각. 종료된 녹음은 null", nullable = true) Instant expiresAt,
        @Schema(description = "종료 시각. 진행 중이면 null", nullable = true) Instant endedAt,
        @Schema(description = "종료 사유. 진행 중이거나 기록 전 종료면 null", nullable = true) RecordingEndReason endReason,
        @Schema(description = "응답을 만든 서버 시각") Instant serverNow
) {

    public static RecordingHeartbeatResponse from(RecordingHeartbeatResult result) {
        return new RecordingHeartbeatResponse(
                result.recordingId(),
                result.status(),
                result.elapsedMillis(),
                result.lastSeenAt(),
                result.expiresAt(),
                result.endedAt(),
                result.endReason(),
                result.serverNow()
        );
    }
}

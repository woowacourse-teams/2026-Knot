package com.knot.backend.recording.presentation.dto.response;

import com.knot.backend.recording.application.dto.result.RecordingDetailResult;
import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import com.knot.backend.recording.domain.RecordingEndReason;
import com.knot.backend.recording.domain.RecordingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RecordingDetailResponse(
        @Schema(description = "녹음 세션 ID") long recordingId,
        @Schema(description = "녹음 세션 상태") RecordingStatus status,
        @Schema(description = "녹음 시작 시각") Instant startedAt,
        @Schema(description = "일시정지를 뺀 서버 기준 녹음 시간") long elapsedMillis,
        @Schema(description = "종료 시각. 진행 중이면 null", nullable = true) Instant endedAt,
        @Schema(description = "종료 사유. 진행 중이거나 기록 전 종료면 null", nullable = true) RecordingEndReason endReason,
        @Schema(description = "신호가 없으면 연결 만료로 종료되는 시각. 종료된 녹음은 null", nullable = true) Instant expiresAt,
        @Schema(description = "응답을 만든 서버 시각") Instant serverNow,
        @Schema(description = "최종 오디오 업로드 상태. 예약 전 null", nullable = true) RecordingAudioUploadStatus audioUploadStatus,
        @Schema(description = "업로드 예약 ID. 예약 전이면 null", nullable = true) Long uploadId,
        @Schema(description = "업로드 저장 확인 시각. 완료 전이면 null", nullable = true) Instant completedAt,
        @Schema(description = "일시정지를 뺀 최대 녹음 시간(밀리초)", example = "7200000") long maxDurationMillis
) {

    public static RecordingDetailResponse from(RecordingDetailResult result) {
        return new RecordingDetailResponse(
                result.recordingId(),
                result.status(),
                result.startedAt(),
                result.elapsedMillis(),
                result.endedAt(),
                result.endReason(),
                result.expiresAt(),
                result.serverNow(),
                result.audioUploadStatus(),
                result.uploadId(),
                result.completedAt(),
                result.maxDurationMillis()
        );
    }
}

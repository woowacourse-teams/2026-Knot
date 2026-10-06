package com.knot.backend.recording.presentation.dto.response;

import com.knot.backend.recording.application.dto.result.RecordingAudioUploadCompletionResult;
import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RecordingAudioUploadCompletionResponse(
        @Schema(description = "녹음 세션 ID") long recordingId,
        @Schema(description = "업로드 예약 ID") long uploadId,
        @Schema(description = "항상 COMPLETED. 전사 처리 상태와 구분한다") RecordingAudioUploadStatus uploadStatus,
        @Schema(description = "서버가 업로드 완료를 확정한 시각. 재호출해도 바뀌지 않는다") Instant completedAt
) {

    public static RecordingAudioUploadCompletionResponse from(RecordingAudioUploadCompletionResult result) {
        return new RecordingAudioUploadCompletionResponse(
                result.recordingId(),
                result.uploadId(),
                result.uploadStatus(),
                result.completedAt()
        );
    }
}

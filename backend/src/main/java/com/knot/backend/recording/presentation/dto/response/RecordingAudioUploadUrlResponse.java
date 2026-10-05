package com.knot.backend.recording.presentation.dto.response;

import com.knot.backend.recording.application.dto.result.RecordingAudioUploadUrlResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RecordingAudioUploadUrlResponse(
        @Schema(description = "업로드 예약 ID. 업로드 완료 확인에 사용한다") long uploadId,
        @Schema(description = "객체 저장소에 직접 PUT할 URL") String uploadUrl,
        @Schema(description = "URL 만료 시각. 만료되면 같은 요청으로 다시 발급받는다") Instant expiresAt
) {

    public static RecordingAudioUploadUrlResponse from(RecordingAudioUploadUrlResult result) {
        return new RecordingAudioUploadUrlResponse(
                result.uploadId(),
                result.uploadUrl(),
                result.expiresAt()
        );
    }
}

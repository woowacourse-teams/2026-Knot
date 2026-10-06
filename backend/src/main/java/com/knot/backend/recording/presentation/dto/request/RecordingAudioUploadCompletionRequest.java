package com.knot.backend.recording.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// @formatter:off
public record RecordingAudioUploadCompletionRequest(
        @NotNull(message = "업로드 ID는 필수입니다")
        @Positive(message = "업로드 ID가 올바르지 않습니다")
        @Schema(description = "audio-upload-url이 발급한 업로드 예약 ID", example = "300",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long uploadId
) {
    // @formatter:on
}

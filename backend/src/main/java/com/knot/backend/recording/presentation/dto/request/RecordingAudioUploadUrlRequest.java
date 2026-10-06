package com.knot.backend.recording.presentation.dto.request;

import com.knot.backend.recording.application.dto.command.RecordingAudioUploadUrlCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// @formatter:off
public record RecordingAudioUploadUrlRequest(
        @NotBlank(message = "오디오 Content-Type은 필수입니다")
        @Schema(description = "최종 오디오 파일의 Content-Type. PUT 요청에도 같은 값을 보낸다", example = "audio/webm",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String contentType,

        @NotNull(message = "오디오 크기는 필수입니다")
        @Positive(message = "오디오 크기는 1바이트 이상이어야 합니다")
        @Schema(description = "최종 오디오 파일 크기(byte). PUT 요청의 Content-Length와 같아야 한다", example = "5242880",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long contentLength
) {
    // @formatter:on

    public RecordingAudioUploadUrlCommand toCommand() {
        return new RecordingAudioUploadUrlCommand(
                contentType,
                contentLength
        );
    }
}

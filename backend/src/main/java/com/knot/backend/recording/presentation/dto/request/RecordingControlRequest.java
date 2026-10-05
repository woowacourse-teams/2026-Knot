package com.knot.backend.recording.presentation.dto.request;

import com.knot.backend.recording.application.dto.command.RecordingControlCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

// @formatter:off
public record RecordingControlRequest(
        @NotNull(message = "녹음 탭 ID는 필수입니다")
        @Schema(description = "녹음을 시작한 최초 탭의 UUID", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID tabId,

        @NotNull(message = "녹음 제어 증명은 필수입니다")
        @Pattern(regexp = "[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]", message = "녹음 제어 증명 형식이 올바르지 않습니다")
        @Schema(description = "녹음 시작 때 보낸 제어 증명. 패딩 없는 Base64URL",
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        String controlToken
) {
    // @formatter:on

    public RecordingControlCommand toCommand() {
        return new RecordingControlCommand(
                tabId,
                controlToken
        );
    }

    @Override
    public String toString() {
        return "RecordingControlRequest[controlToken=REDACTED]";
    }
}

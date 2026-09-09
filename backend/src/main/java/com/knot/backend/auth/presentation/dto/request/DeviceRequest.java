package com.knot.backend.auth.presentation.dto.request;

import com.knot.backend.auth.domain.DeviceInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "토큰을 발급받는 기기. 기기 목록에 그대로 보인다")
public record DeviceRequest(
        @Schema(description = "기기 이름", example = "건규의 MacBook Pro") @NotBlank @Size(
                max = DeviceInfo.MAX_NAME_LENGTH
        ) String name,
        @Schema(description = "플랫폼", example = "darwin") @NotBlank @Size(
                max = DeviceInfo.MAX_PLATFORM_LENGTH
        ) String platform,
        @Schema(description = "앱 버전", example = "0.1.0", nullable = true) @Size(
                max = DeviceInfo.MAX_APP_VERSION_LENGTH
        ) String appVersion
) {

    public DeviceInfo toDeviceInfo() {
        return new DeviceInfo(
                name,
                platform,
                appVersion
        );
    }
}

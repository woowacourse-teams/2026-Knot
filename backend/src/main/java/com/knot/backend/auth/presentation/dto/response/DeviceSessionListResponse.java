package com.knot.backend.auth.presentation.dto.response;

import com.knot.backend.auth.application.dto.result.DeviceSessionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "기기 세션 목록(기획서 5.2)")
public record DeviceSessionListResponse(
        List<DeviceSessionResponse> sessions
) {

    public static DeviceSessionListResponse from(List<DeviceSessionResult> results) {
        return new DeviceSessionListResponse(
                results.stream()
                        .map(DeviceSessionResponse::from)
                        .toList()
        );
    }

    @Schema(description = "기기 세션")
    public record DeviceSessionResponse(
            @Schema(description = "세션 ID", example = "1") long id,
            @Schema(description = "기기 이름") String deviceName,
            @Schema(description = "플랫폼", example = "darwin") String platform,
            @Schema(description = "앱 버전", nullable = true) String appVersion,
            @Schema(description = "로그인 시각") Instant createdAt,
            @Schema(description = "마지막 토큰 발급·갱신 시각") Instant lastUsedAt,
            @Schema(description = "요청한 토큰의 세션인지. 웹 액세스 토큰으로 조회하면 모두 false") boolean current
    ) {

        public static DeviceSessionResponse from(DeviceSessionResult result) {
            return new DeviceSessionResponse(
                    result.id(),
                    result.deviceName(),
                    result.platform(),
                    result.appVersion(),
                    result.createdAt(),
                    result.lastUsedAt(),
                    result.current()
            );
        }
    }
}

package com.knot.backend.auth.presentation.dto.response;

import com.knot.backend.auth.application.dto.result.DeviceTokenResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 디바이스 코드 교환·리프레시 응답(기획서 5.2).
 *
 * `requiresNickname`이 false면 액세스·리프레시 토큰과 세션이, true면 온보딩 토큰만 있다(로드맵 Q57). 온보딩을 마친 뒤 받는
 * 액세스 토큰은 웹과 같은 1시간짜리이며, 다음 로그인부터 리프레시 토큰이 붙는다.
 */
@Schema(description = "디바이스 토큰 응답")
public record DeviceTokenResponse(
        @Schema(description = "닉네임 설정이 필요한 신규 사용자인지") boolean requiresNickname,
        @Schema(description = "토큰 타입", example = "Bearer", nullable = true) String tokenType,
        @Schema(description = "액세스 토큰(JWT, DEVICE_ACCESS)", nullable = true) String accessToken,
        @Schema(description = "리프레시 토큰(불투명, 1회용)", nullable = true) String refreshToken,
        @Schema(description = "액세스 토큰 만료(초)", example = "3600", nullable = true) Long expiresIn,
        @Schema(description = "온보딩 토큰(닉네임 설정용)", nullable = true) String onboardingToken,
        @Schema(description = "온보딩 토큰 만료(초)", example = "600", nullable = true) Long onboardingExpiresIn,
        @Schema(description = "만들어진 기기 세션", nullable = true) DeviceSessionSummaryResponse session
) {
    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static DeviceTokenResponse from(DeviceTokenResult result) {
        if (result.requiresNickname()) {
            return new DeviceTokenResponse(
                    true,
                    null,
                    null,
                    null,
                    null,
                    result.onboardingToken(),
                    result.onboardingExpiresIn()
                            .toSeconds(),
                    null
            );
        }
        return new DeviceTokenResponse(
                false,
                BEARER_TOKEN_TYPE,
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn()
                        .toSeconds(),
                null,
                null,
                new DeviceSessionSummaryResponse(
                        result.sessionId(),
                        result.deviceName()
                )
        );
    }

    @Schema(description = "기기 세션 요약")
    public record DeviceSessionSummaryResponse(
            @Schema(description = "세션 ID", example = "1") long id,
            @Schema(description = "기기 이름") String deviceName
    ) {
    }
}

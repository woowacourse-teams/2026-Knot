package com.knot.backend.auth.application.dto.result;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import java.time.Duration;

/**
 * 디바이스 코드 교환·리프레시 결과(기획서 5.2).
 *
 * 기존 회원이면 액세스·리프레시 토큰과 세션, 닉네임을 아직 정하지 않은 사용자면 온보딩 토큰만 있다(로드맵 Q57). 두 경우를 한
 * 타입으로 두는 이유는 교환 엔드포인트가 하나이고, 앱이 `requiresNickname`으로 분기하기 때문이다.
 */
public record DeviceTokenResult(
        boolean requiresNickname,
        String accessToken,
        String refreshToken,
        Duration expiresIn,
        Long sessionId,
        String deviceName,
        String onboardingToken,
        Duration onboardingExpiresIn
) {
    public DeviceTokenResult {
        if (requiresNickname) {
            if (onboardingToken == null || onboardingToken.isBlank() || onboardingExpiresIn == null) {
                throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
            }
            if (accessToken != null || refreshToken != null || sessionId != null) {
                throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
            }
        } else {
            if (accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank()
                    || expiresIn == null || sessionId == null || deviceName == null) {
                throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
            }
            if (onboardingToken != null || onboardingExpiresIn != null) {
                throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
            }
        }
    }

    public static DeviceTokenResult authenticated(
            String accessToken,
            String refreshToken,
            Duration expiresIn,
            long sessionId,
            String deviceName
    ) {
        return new DeviceTokenResult(
                false,
                accessToken,
                refreshToken,
                expiresIn,
                sessionId,
                deviceName,
                null,
                null
        );
    }

    public static DeviceTokenResult nicknameSetupRequired(
            String onboardingToken,
            Duration onboardingExpiresIn
    ) {
        return new DeviceTokenResult(
                true,
                null,
                null,
                null,
                null,
                null,
                onboardingToken,
                onboardingExpiresIn
        );
    }
}

package com.knot.backend.auth.domain;

/**
 * 디바이스 토큰을 발급받는 기기의 식별 정보(기획서 5.2 `device:{name, platform, appVersion}`).
 *
 * 사용자가 기기 목록에서 알아볼 수 있는 이름과 플랫폼만 받는다. 값은 앱이 보내는 것이라 신뢰하지 않으며 길이만 제한한다.
 */
public record DeviceInfo(
        String name,
        String platform,
        String appVersion
) {
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_PLATFORM_LENGTH = 20;
    public static final int MAX_APP_VERSION_LENGTH = 50;

    public DeviceInfo {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new AuthException(AuthErrorCode.INVALID_DEVICE_INFO);
        }
        if (platform == null || platform.isBlank() || platform.length() > MAX_PLATFORM_LENGTH) {
            throw new AuthException(AuthErrorCode.INVALID_DEVICE_INFO);
        }
        if (appVersion != null && (appVersion.isBlank() || appVersion.length() > MAX_APP_VERSION_LENGTH)) {
            throw new AuthException(AuthErrorCode.INVALID_DEVICE_INFO);
        }
        name = name.strip();
        platform = platform.strip();
        appVersion = appVersion == null ? null : appVersion.strip();
    }
}

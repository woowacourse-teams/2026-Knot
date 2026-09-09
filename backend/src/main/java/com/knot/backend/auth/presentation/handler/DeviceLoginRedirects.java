package com.knot.backend.auth.presentation.handler;

import com.knot.backend.auth.domain.DeviceLoginRequest;

/**
 * 데스크톱 로그인의 브라우저 → 앱 복귀 URL(기획서 5.2 시퀀스).
 *
 * 성공은 `?code=&state=`, 실패는 `?error=&state=`다. 실패도 앱이 기다리는 loopback·딥링크로 보내 앱의 대기가 풀리게 한다
 * (로드맵 Q56). 오류 값은 웹 실패 리다이렉트와 같은 `oauth2` 하나뿐이라 원인이 새지 않는다.
 */
public final class DeviceLoginRedirects {
    public static final String OAUTH_ERROR = "oauth2";

    private DeviceLoginRedirects() {}

    public static String success(
            DeviceLoginRequest deviceLogin,
            String code
    ) {
        return deviceLogin.returnTarget()
                .successUri(
                        code,
                        deviceLogin.state()
                );
    }

    public static String failure(DeviceLoginRequest deviceLogin) {
        return deviceLogin.returnTarget()
                .failureUri(
                        OAUTH_ERROR,
                        deviceLogin.state()
                );
    }
}

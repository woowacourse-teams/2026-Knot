package com.knot.backend.auth.domain;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * `GET /oauth2/authorization/github?client=desktop&…`이 실어 온 데스크톱 로그인 요청(기획서 5.2).
 *
 * OAuth 인가 요청의 attributes에 보관됐다가 콜백에서 성공 핸들러가 꺼내 쓴다. `state`는 앱이 만든 값이며 서버는 URL이나
 * 민감 정보를 넣지 않고 그대로 돌려준다(5.3 open redirector 방지). PKCE는 S256만 받는다(RFC 7636 §4.2).
 */
public record DeviceLoginRequest(
        String codeChallenge,
        String state,
        DeviceReturnTarget returnTarget
) implements Serializable {
    public static final String CLIENT_PARAMETER = "client";
    public static final String DESKTOP_CLIENT = "desktop";
    public static final String CODE_CHALLENGE_PARAMETER = "code_challenge";
    public static final String CODE_CHALLENGE_METHOD_PARAMETER = "code_challenge_method";
    public static final String STATE_PARAMETER = "state";
    public static final String RETURN_PARAMETER = "return";
    public static final String S256_METHOD = "S256";

    private static final Pattern CODE_CHALLENGE_PATTERN = Pattern.compile("^[A-Za-z0-9._~-]{43,128}$");
    private static final Pattern STATE_PATTERN = Pattern.compile("^[A-Za-z0-9._~-]{1,256}$");

    public DeviceLoginRequest {
        if (codeChallenge == null || !CODE_CHALLENGE_PATTERN.matcher(codeChallenge)
                .matches()) {
            throw new AuthException(AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID);
        }
        if (state == null || !STATE_PATTERN.matcher(state)
                .matches()) {
            throw new AuthException(AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID);
        }
        if (returnTarget == null) {
            throw new AuthException(AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID);
        }
    }

    public static DeviceLoginRequest of(
            String codeChallenge,
            String codeChallengeMethod,
            String state,
            String returnValue
    ) {
        if (!S256_METHOD.equals(codeChallengeMethod)) {
            throw new AuthException(AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID);
        }
        return new DeviceLoginRequest(
                codeChallenge,
                state,
                DeviceReturnTarget.parse(returnValue)
        );
    }
}

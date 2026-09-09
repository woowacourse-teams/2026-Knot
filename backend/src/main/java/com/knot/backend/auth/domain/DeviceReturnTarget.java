package com.knot.backend.auth.domain;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 시스템 브라우저 로그인이 끝난 뒤 앱으로 돌아가는 목적지(기획서 5.2·5.3).
 *
 * `return` 파라미터는 `loopback:{port}`·`deeplink` 두 값만 허용한다. 목적지 URL은 서버가 여기서 조립하므로 외부에서 임의
 * URL을 넣어 열린 리다이렉터가 되는 일이 없다. loopback은 RFC 8252 §7.3대로 `127.0.0.1`에만 보낸다.
 */
public sealed interface DeviceReturnTarget extends Serializable permits DeviceReturnTarget.Loopback,
        DeviceReturnTarget.DeepLink {
    String LOOPBACK_PREFIX = "loopback:";
    String DEEP_LINK_VALUE = "deeplink";
    String DEEP_LINK_CALLBACK_URI = "knot://auth/callback";
    Pattern LOOPBACK_PATTERN = Pattern.compile("^loopback:(\\d{4,5})$");
    int MIN_LOOPBACK_PORT = 1024;
    int MAX_LOOPBACK_PORT = 65535;

    static DeviceReturnTarget parse(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthException(AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID);
        }
        if (DEEP_LINK_VALUE.equals(value)) {
            return new DeepLink();
        }
        Matcher matcher = LOOPBACK_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new AuthException(AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID);
        }
        int port = Integer.parseInt(matcher.group(1));
        if (port < MIN_LOOPBACK_PORT || port > MAX_LOOPBACK_PORT) {
            throw new AuthException(AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID);
        }
        return new Loopback(port);
    }

    /** 앱 콜백 주소. 쿼리 없이 돌려주며 호출자가 `code`·`state` 또는 `error`를 붙인다 */
    String callbackUri();

    default String successUri(
            String code,
            String state
    ) {
        return UriComponentsBuilder.fromUriString(callbackUri())
                .queryParam(
                        "code",
                        code
                )
                .queryParam(
                        "state",
                        state
                )
                .build()
                .toUriString();
    }

    default String failureUri(
            String error,
            String state
    ) {
        return UriComponentsBuilder.fromUriString(callbackUri())
                .queryParam(
                        "error",
                        error
                )
                .queryParam(
                        "state",
                        state
                )
                .build()
                .toUriString();
    }

    record Loopback(int port) implements DeviceReturnTarget {
        public Loopback {
            if (port < MIN_LOOPBACK_PORT || port > MAX_LOOPBACK_PORT) {
                throw new AuthException(AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID);
            }
        }

        @Override
        public String callbackUri() {
            return "http://127.0.0.1:" + port + "/callback";
        }
    }

    record DeepLink() implements DeviceReturnTarget {
        @Override
        public String callbackUri() {
            return DEEP_LINK_CALLBACK_URI;
        }
    }
}

package com.knot.backend.auth.presentation.dto.response;

import java.time.Duration;

/**
 * 액세스 토큰 발급 응답 (기획서 5.1).
 *
 * 쿠키를 심지 않고 본문으로 돌려주므로 클라이언트가 저장 위치를 고른다. 웹은 `localStorage`, 데스크톱은 main의
 * `safeStorage`다.
 */
public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static AccessTokenResponse of(
            String accessToken,
            Duration expiration
    ) {
        return new AccessTokenResponse(
                accessToken,
                BEARER_TOKEN_TYPE,
                expiration.toSeconds()
        );
    }
}

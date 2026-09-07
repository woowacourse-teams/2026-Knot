package com.knot.backend.auth.infrastructure.jwt;

/**
 * `Authorization` 헤더에서 Bearer 토큰만 꺼낸다.
 *
 * 인증 자격증명은 Bearer 하나뿐이므로(기획서 D11·5.1) 헤더를 읽는 곳도 필터와 온보딩 완료 엔드포인트 둘뿐이다. 두 곳이 같은
 * 규칙을 쓰도록 여기 모아 둔다.
 */
public final class BearerTokenResolver {
    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokenResolver() {}

    /**
     * @param authorizationHeader
     *            `Authorization` 헤더 값. 없으면 null
     * @return Bearer 토큰. 헤더가 없거나 Bearer 스킴이 아니거나 값이 비면 null
     */
    public static String resolve(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        if (!authorizationHeader.regionMatches(
                true,
                0,
                BEARER_PREFIX,
                0,
                BEARER_PREFIX.length()
        )) {
            return null;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length())
                .trim();
        if (token.isEmpty()) {
            return null;
        }
        return token;
    }
}

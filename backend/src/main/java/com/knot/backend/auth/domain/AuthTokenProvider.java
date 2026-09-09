package com.knot.backend.auth.domain;

public interface AuthTokenProvider {
    String issue(AuthenticatedMember member);

    /** 기기 세션에 묶인 액세스 토큰(`token_type=DEVICE_ACCESS`, `sid`). 만료는 `issue`와 같다(기획서 5.2 토큰 규격) */
    String issueDevice(
            AuthenticatedMember member,
            long deviceSessionId
    );

    String issueNickname(OAuthUser oauthUser);

    /** `ACCESS`·`DEVICE_ACCESS` 둘 다 받는다. 후자는 `deviceSessionId`가 채워진 주체를 돌려준다 */
    AuthenticatedMember authenticate(String token);

    OAuthUser authenticateNickname(String token);
}

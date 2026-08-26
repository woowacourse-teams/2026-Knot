package com.knot.backend.auth.domain;

public interface AuthTokenProvider {
    String issue(AuthenticatedMember member);

    String issueNickname(OAuthUser oauthUser);

    AuthenticatedMember authenticate(String token);

    OAuthUser authenticateNickname(String token);
}

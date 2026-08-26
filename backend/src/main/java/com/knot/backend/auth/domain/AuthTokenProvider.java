package com.knot.backend.auth.domain;

import com.knot.backend.member.domain.Member;

public interface AuthTokenProvider {

    String issue(Member member);

    String issue(AuthenticatedMember member);

    String issueNickname(OAuthUser oauthUser);

    AuthenticatedMember authenticate(String token);

    OAuthUser authenticateNickname(String token);
}

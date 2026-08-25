package com.knot.backend.auth.domain;

import com.knot.backend.member.domain.Member;

public interface AuthTokenProvider {

    String issue(Member member);

    String issue(AuthenticatedMember member);

    AuthenticatedMember authenticate(String token);
}

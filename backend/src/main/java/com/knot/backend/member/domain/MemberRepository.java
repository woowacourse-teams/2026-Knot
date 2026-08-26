package com.knot.backend.member.domain;

import java.util.Optional;

public interface MemberRepository {

    Optional<Member> findById(long memberId);

    Member save(Member member);
}

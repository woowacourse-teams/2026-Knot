package com.knot.backend.member.domain;

import java.util.Optional;

public interface MemberRepository {

    Optional<Member> findById(long memberId);

    boolean existsById(long memberId);

    Member save(Member member);
}

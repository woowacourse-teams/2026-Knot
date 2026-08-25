package com.knot.backend.member.domain;

import java.util.Optional;

public interface MemberRepository {

    Optional<Member> findByGithubId(long githubId);

    void insertIfAbsent(Member member);

    Member save(Member member);
}

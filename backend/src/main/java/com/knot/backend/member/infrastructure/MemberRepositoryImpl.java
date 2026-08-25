package com.knot.backend.member.infrastructure;

import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Optional<Member> findByGithubId(long githubId) {
        return memberJpaRepository.findByGithubId(githubId);
    }

    @Override
    public void insertIfAbsent(Member member) {
        memberJpaRepository.insertIfAbsent(
                member.getGithubId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );
    }

    @Override
    public Member save(Member member) {
        return memberJpaRepository.save(member);
    }
}

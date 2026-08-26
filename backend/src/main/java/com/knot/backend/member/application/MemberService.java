package com.knot.backend.member.application;

import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberErrorCode;
import com.knot.backend.member.domain.MemberException;
import com.knot.backend.member.domain.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public Optional<Member> findById(long memberId) {
        if (memberId <= 0) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }

        return memberRepository.findById(memberId);
    }

    @Transactional
    public Member create(
            String nickname,
            String profileImageUrl
    ) {
        Member member = Member.create(
                nickname,
                profileImageUrl
        );

        return memberRepository.save(member);
    }

    @Transactional
    public Member updateProfile(
            Member member,
            String nickname,
            String profileImageUrl
    ) {
        if (member == null) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }

        member.updateProfile(
                nickname,
                profileImageUrl
        );

        return memberRepository.save(member);
    }
}

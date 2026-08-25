package com.knot.backend.member.application;

import com.knot.backend.auth.domain.OAuthUser;
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

    @Transactional
    public Member login(OAuthUser oauthUser) {
        validateOAuthUser(oauthUser);
        Member member = Member.create(
                oauthUser.getExternalId(),
                oauthUser.getNickname(),
                oauthUser.getProfileImageUrl()
        );
        memberRepository.insertIfAbsent(member);
        Member loggedInMember = memberRepository.findByGithubId(member.getGithubId())
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_LOGIN_FAILED));
        loggedInMember.updateProfile(oauthUser);
        return memberRepository.save(loggedInMember);
    }

    @Transactional(readOnly = true)
    public Optional<Member> findByGithubId(long githubId) {
        if (githubId <= 0) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
        return memberRepository.findByGithubId(githubId);
    }

    @Transactional
    public Member create(OAuthUser oauthUser) {
        validateOAuthUser(oauthUser);
        return memberRepository.save(
                Member.create(
                        oauthUser.getExternalId(),
                        oauthUser.getNickname(),
                        oauthUser.getProfileImageUrl()
                )
        );
    }

    @Transactional
    public Member updateProfile(
            Member member,
            OAuthUser oauthUser
    ) {
        if (member == null) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
        validateOAuthUser(oauthUser);
        member.updateProfile(oauthUser);
        return memberRepository.save(member);
    }

    private void validateOAuthUser(OAuthUser oauthUser) {
        if (oauthUser == null) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
    }
}

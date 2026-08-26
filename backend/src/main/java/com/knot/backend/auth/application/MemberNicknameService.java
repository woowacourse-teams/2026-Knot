package com.knot.backend.auth.application;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberNicknameService {
    private final MemberService memberService;
    private final OAuthIdentityService oauthIdentityService;

    @Transactional
    public Member completeNickname(
            OAuthUser oauthUser,
            String nickname
    ) {
        if (oauthUser == null) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }

        Member member = memberService.create(
                nickname,
                oauthUser.getProfileImageUrl()
        );

        OAuthIdentity identity = OAuthIdentity.create(
                oauthUser,
                member.getId()
        );

        oauthIdentityService.save(identity);

        return member;
    }
}

package com.knot.backend.auth.application;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.domain.Member;
import com.knot.backend.member.application.MemberService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberService memberService;
    private final OAuthIdentityService oauthIdentityService;
    private final MemberNicknameService memberNicknameService;
    private final AuthTokenProvider authTokenProvider;

    public AuthLoginResult login(OAuthUser oauthUser) {
        if (oauthUser == null) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }

        return oauthIdentityService.findByProviderAndProviderUserId(
                oauthUser.getProvider(),
                oauthUser.getExternalId()
        )
                .map(this::issueAccessToken)
                .orElseGet(() -> issueNicknameToken(oauthUser));
    }

    public String completeNickname(
            String nicknameToken,
            String nickname
    ) {
        OAuthUser oauthUser = authTokenProvider.authenticateNickname(nicknameToken);
        Member member = memberNicknameService.completeNickname(
                oauthUser,
                nickname
        );
        return authTokenProvider.issue(member);
    }

    private AuthLoginResult issueAccessToken(OAuthIdentity identity) {
        Optional<Member> member = memberService.findById(identity.getMemberId());
        Member authenticatedMember = member.orElseThrow(
                () -> new AuthException(AuthErrorCode.MEMBER_NOT_FOUND_FOR_OAUTH_IDENTITY)
        );

        return AuthLoginResult.authenticated(authTokenProvider.issue(authenticatedMember));
    }

    private AuthLoginResult issueNicknameToken(OAuthUser oauthUser) {
        return AuthLoginResult.nickname(authTokenProvider.issueNickname(oauthUser));
    }
}

package com.knot.backend.auth.application;

import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.application.dto.result.AuthLoginResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
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

    public String completeNicknameSetup(CompleteNicknameCommand command) {
        OAuthUser oauthUser = authTokenProvider.authenticateNickname(command.nicknameToken());
        Member member = memberNicknameService.completeNicknameSetup(
                oauthUser,
                command.nickname()
        );
        AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );
        return authTokenProvider.issue(authenticatedMember);
    }

    private AuthLoginResult issueAccessToken(OAuthIdentity identity) {
        Member member = memberService.findById(identity.getMemberId())
                .orElseThrow(
                        () -> new AuthException(AuthErrorCode.MEMBER_NOT_FOUND_FOR_OAUTH_IDENTITY)
                );
        AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );

        return AuthLoginResult.authenticated(authTokenProvider.issue(authenticatedMember));
    }

    private AuthLoginResult issueNicknameToken(OAuthUser oauthUser) {
        return AuthLoginResult.nicknameSetupRequired(authTokenProvider.issueNickname(oauthUser));
    }
}

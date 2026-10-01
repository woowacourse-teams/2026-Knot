package com.knot.backend.auth.application;

import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.application.dto.result.AuthLoginResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.AuthSession;
import com.knot.backend.auth.domain.AuthSessionRepository;
import com.knot.backend.auth.domain.RefreshToken;
import com.knot.backend.auth.domain.RefreshTokenProvider;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberService memberService;
    private final OAuthIdentityService oauthIdentityService;
    private final MemberNicknameService memberNicknameService;
    private final AuthTokenProvider authTokenProvider;
    private final AuthSessionRepository sessionRepository;
    private final RefreshTokenProvider refreshTokenProvider;
    private final Clock clock;

    @Transactional
    public AuthLoginResult login(OAuthUser oauthUser) {
        if (oauthUser == null) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }

        return oauthIdentityService.findByProviderAndProviderUserId(
                oauthUser.getProvider(),
                oauthUser.getExternalId()
        )
                .map(this::createMemberLogin)
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

    private AuthLoginResult createMemberLogin(OAuthIdentity identity) {
        Member member = memberService.findById(identity.getMemberId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.MEMBER_NOT_FOUND_FOR_OAUTH_IDENTITY));
        if (member.isDeleted()) {
            throw new AuthException(AuthErrorCode.MEMBER_WITHDRAWN);
        }
        AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );

        String accessToken = authTokenProvider.issue(authenticatedMember);
        RefreshToken refreshToken = refreshTokenProvider.issue();
        Instant now = clock.instant();
        AuthSession session = AuthSession.create(member.getId(), refreshToken.getHash(), now);
        sessionRepository.save(session);

        return AuthLoginResult.authenticated(accessToken, refreshToken.getValue(), Duration.between(now, session.getExpiresAt()));
    }

    private AuthLoginResult issueNicknameToken(OAuthUser oauthUser) {
        return AuthLoginResult.nicknameSetupRequired(authTokenProvider.issueNickname(oauthUser));
    }
}

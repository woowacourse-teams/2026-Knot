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
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Member member = getActiveMember(identity.getMemberId());
        AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );

        String accessToken = authTokenProvider.issue(authenticatedMember);
        RefreshToken refreshToken = refreshTokenProvider.issue();
        Instant now = clock.instant();
        AuthSession session = createLoginSession(
                member.getId(),
                refreshToken.getHash(),
                now
        );

        return AuthLoginResult.authenticated(
                accessToken,
                refreshToken.getValue(),
                session.remainingRefreshLifetime(now)
        );
    }

    private Member getActiveMember(long memberId) {
        Member member = memberService.findById(memberId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.MEMBER_NOT_FOUND_FOR_OAUTH_IDENTITY));
        if (member.isDeleted()) {
            throw new AuthException(AuthErrorCode.MEMBER_WITHDRAWN);
        }
        return member;
    }

    private AuthSession createLoginSession(
            long memberId,
            String refreshTokenHash,
            Instant loginAt
    ) {
        AuthSession session = AuthSession.create(
                memberId,
                refreshTokenHash,
                loginAt
        );
        sessionRepository.save(session);
        return session;
    }

    private AuthLoginResult issueNicknameToken(OAuthUser oauthUser) {
        return AuthLoginResult.nicknameSetupRequired(authTokenProvider.issueNickname(oauthUser));
    }
}

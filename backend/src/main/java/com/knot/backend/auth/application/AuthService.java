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

    @Transactional
    public AuthLoginResult completeNicknameSetup(CompleteNicknameCommand command) {
        OAuthUser oauthUser = authTokenProvider.authenticateNickname(command.nicknameToken());
        Instant initialOAuthLoginAt = oauthUser.getAuthenticatedAt();
        if (initialOAuthLoginAt == null) {
            throw new AuthException(AuthErrorCode.INVALID_JWT);
        }
        Member member = memberNicknameService.completeNicknameSetup(
                oauthUser,
                command.nickname()
        );
        return issueMemberTokens(
                member,
                clock.instant(),
                initialOAuthLoginAt
        );
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }

        String refreshTokenHash = refreshTokenProvider.hash(refreshTokenValue);
        Instant revokedAt = clock.instant();
        sessionRepository.findByRefreshTokenHash(refreshTokenHash)
                .ifPresent(session -> {
                    if (session.revoke(revokedAt)) {
                        sessionRepository.save(session);
                    }
                });
    }

    private AuthLoginResult createMemberLogin(OAuthIdentity identity) {
        Member member = getActiveMember(identity.getMemberId());
        Instant loginAt = clock.instant();
        return issueMemberTokens(
                member,
                loginAt,
                loginAt
        );
    }

    private AuthLoginResult issueMemberTokens(
            Member member,
            Instant sessionCreatedAt,
            Instant initialOAuthLoginAt
    ) {
        AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );

        String accessToken = authTokenProvider.issue(authenticatedMember);
        Instant refreshExpiresAt = AuthSession.initialRefreshExpirationAt(
                sessionCreatedAt,
                initialOAuthLoginAt
        );
        RefreshToken refreshToken = refreshTokenProvider.issue(refreshExpiresAt);
        AuthSession session = createLoginSession(
                member.getId(),
                refreshToken.getHash(),
                sessionCreatedAt,
                initialOAuthLoginAt
        );

        return AuthLoginResult.authenticated(
                accessToken,
                refreshToken.getValue(),
                session.remainingRefreshLifetime(sessionCreatedAt)
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
            Instant sessionCreatedAt,
            Instant initialOAuthLoginAt
    ) {
        AuthSession session = AuthSession.create(
                memberId,
                refreshTokenHash,
                sessionCreatedAt,
                initialOAuthLoginAt
        );
        sessionRepository.save(session);
        return session;
    }

    private AuthLoginResult issueNicknameToken(OAuthUser oauthUser) {
        return AuthLoginResult.nicknameSetupRequired(authTokenProvider.issueNickname(oauthUser));
    }
}

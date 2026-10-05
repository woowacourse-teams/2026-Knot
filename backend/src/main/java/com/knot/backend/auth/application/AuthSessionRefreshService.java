package com.knot.backend.auth.application;

import com.knot.backend.auth.application.dto.result.AuthRefreshResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthSession;
import com.knot.backend.auth.domain.AuthSessionRepository;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.RefreshToken;
import com.knot.backend.auth.domain.ConsumedRefreshToken;
import com.knot.backend.auth.domain.ConsumedRefreshTokenRepository;
import com.knot.backend.auth.domain.RefreshTokenProvider;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionRefreshService {
    private final AuthSessionRepository authSessionRepository;
    private final ConsumedRefreshTokenRepository consumedRefreshTokenRepository;
    private final RefreshTokenProvider refreshTokenProvider;
    private final AuthTokenProvider authTokenProvider;
    private final MemberService memberService;
    private final Clock clock;

    @Transactional
    public Optional<AuthRefreshResult> refresh(String rawRefreshToken) {
        RefreshToken currentToken = refreshTokenProvider.identify(rawRefreshToken);
        Instant now = clock.instant();
        if (revokeSessionIfTokenWasConsumed(
                currentToken,
                now
        )) {
            return Optional.empty();
        }

        if (validateCurrentTokenOrHandleConcurrentReplay(
                currentToken,
                now
        )) {
            return Optional.empty();
        }
        Optional<AuthSession> sessionResult = authSessionRepository
                .findByRefreshTokenHashForUpdate(currentToken.getHash());
        if (sessionResult.isEmpty()) {
            if (revokeSessionIfTokenWasConsumed(
                    currentToken,
                    now
            )) {
                return Optional.empty();
            }
            throw new AuthException(AuthErrorCode.UNAUTHENTICATED);
        }

        AuthSession session = sessionResult.orElseThrow();
        if (!session.isActive(now)) {
            throw new AuthException(AuthErrorCode.UNAUTHENTICATED);
        }
        Member member = findActiveMember(session.getMemberId());
        return rotateSessionToken(
                session,
                member,
                now
        );
    }

    private boolean validateCurrentTokenOrHandleConcurrentReplay(
            RefreshToken currentToken,
            Instant now
    ) {
        try {
            refreshTokenProvider.validate(currentToken);
            return false;
        } catch (AuthException exception) {
            if (revokeSessionIfTokenWasConsumed(
                    currentToken,
                    now
            )) {
                return true;
            }
            throw exception;
        }
    }

    private boolean revokeSessionIfTokenWasConsumed(
            RefreshToken token,
            Instant now
    ) {
        return consumedRefreshTokenRepository.findByRefreshTokenHash(token.getHash())
                .map(
                        consumedRefreshToken -> revokeSessionForReplay(
                                consumedRefreshToken,
                                now
                        )
                )
                .orElse(false);
    }

    private boolean revokeSessionForReplay(
            ConsumedRefreshToken consumedRefreshToken,
            Instant now
    ) {
        AuthSession session = authSessionRepository.findByIdForUpdate(consumedRefreshToken.getAuthSessionId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR));
        session.revoke(now);
        authSessionRepository.save(session);
        return true;
    }

    private Member findActiveMember(long memberId) {
        Member member = memberService.findById(memberId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.UNAUTHENTICATED));
        if (member.isDeleted()) {
            throw new AuthException(AuthErrorCode.UNAUTHENTICATED);
        }
        return member;
    }

    private Optional<AuthRefreshResult> rotateSessionToken(
            AuthSession session,
            Member member,
            Instant now
    ) {
        Instant refreshExpiresAt = session.nextRefreshExpirationAt(now);
        RefreshToken nextToken = refreshTokenProvider.issue(refreshExpiresAt);
        String consumedTokenHash = session.rotateRefreshToken(
                nextToken.getHash(),
                now
        );
        consumedRefreshTokenRepository.save(
                ConsumedRefreshToken.create(
                        session.getId(),
                        consumedTokenHash,
                        now
                )
        );
        authSessionRepository.save(session);

        AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );
        String accessToken = authTokenProvider.issue(authenticatedMember);
        return Optional.of(
                new AuthRefreshResult(
                        accessToken,
                        nextToken.getValue(),
                        session.remainingRefreshLifetime(now)
                )
        );
    }
}

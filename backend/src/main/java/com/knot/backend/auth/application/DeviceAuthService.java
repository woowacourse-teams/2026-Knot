package com.knot.backend.auth.application;

import com.knot.backend.auth.application.dto.command.ExchangeDeviceCodeCommand;
import com.knot.backend.auth.application.dto.result.DeviceSessionResult;
import com.knot.backend.auth.application.dto.result.DeviceTokenResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.DeviceAuthorizationCode;
import com.knot.backend.auth.domain.DeviceAuthorizationCodeRepository;
import com.knot.backend.auth.domain.DeviceInfo;
import com.knot.backend.auth.domain.DeviceRefreshToken;
import com.knot.backend.auth.domain.DeviceRefreshTokenRepository;
import com.knot.backend.auth.domain.DeviceSession;
import com.knot.backend.auth.domain.DeviceSessionRepository;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.domain.OpaqueToken;
import com.knot.backend.global.config.DeviceAuthProperties;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데스크톱 2단계 인증(기획서 5.2, 로드맵 A6): 일회용 코드 발급·교환, 리프레시 rotation·재사용 감지, 폐기, 기기 목록.
 *
 * 재사용 감지로 세션을 폐기한 뒤에도 요청은 실패해야 하므로 `AuthException`은 롤백하지 않는다. 그래서 폐기가 DB에 남는다.
 */
@Service
public class DeviceAuthService {
    private static final Logger log = LoggerFactory.getLogger(DeviceAuthService.class);
    /** 만료된 코드를 재사용 감지용으로 남겨 두는 기간. 지나면 발급 시점에 정리한다 */
    private static final Duration CONSUMED_CODE_RETENTION = Duration.ofHours(1);

    private final DeviceAuthorizationCodeRepository codeRepository;
    private final DeviceSessionRepository sessionRepository;
    private final DeviceRefreshTokenRepository refreshTokenRepository;
    private final OAuthIdentityService oauthIdentityService;
    private final MemberService memberService;
    private final AuthTokenProvider authTokenProvider;
    private final DeviceAuthProperties deviceProperties;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    /** 생성자가 둘(테스트용 `SecureRandom` 주입)이라 Spring이 고를 것을 명시한다 */
    @Autowired
    public DeviceAuthService(
            DeviceAuthorizationCodeRepository codeRepository,
            DeviceSessionRepository sessionRepository,
            DeviceRefreshTokenRepository refreshTokenRepository,
            OAuthIdentityService oauthIdentityService,
            MemberService memberService,
            AuthTokenProvider authTokenProvider,
            DeviceAuthProperties deviceProperties,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this(
                codeRepository,
                sessionRepository,
                refreshTokenRepository,
                oauthIdentityService,
                memberService,
                authTokenProvider,
                deviceProperties,
                jwtProperties,
                clock,
                new SecureRandom()
        );
    }

    DeviceAuthService(
            DeviceAuthorizationCodeRepository codeRepository,
            DeviceSessionRepository sessionRepository,
            DeviceRefreshTokenRepository refreshTokenRepository,
            OAuthIdentityService oauthIdentityService,
            MemberService memberService,
            AuthTokenProvider authTokenProvider,
            DeviceAuthProperties deviceProperties,
            JwtProperties jwtProperties,
            Clock clock,
            SecureRandom secureRandom
    ) {
        validateProperties(deviceProperties);
        this.codeRepository = codeRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.oauthIdentityService = oauthIdentityService;
        this.memberService = memberService;
        this.authTokenProvider = authTokenProvider;
        this.deviceProperties = deviceProperties;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /**
     * OAuth 로그인이 끝난 데스크톱 사용자에게 일회용 코드를 만든다. 원문 코드는 리다이렉트 URL에만 실리고 DB에는 해시만 남는다.
     */
    @Transactional
    public String issueCode(
            OAuthUser oauthUser,
            String codeChallenge
    ) {
        if (oauthUser == null) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }
        Instant now = Instant.now(clock);
        codeRepository.deleteExpiredBefore(now.minus(CONSUMED_CODE_RETENTION));

        String code = OpaqueToken.generate(secureRandom);
        String codeHash = OpaqueToken.hash(code);
        Instant expiresAt = now.plus(deviceProperties.getCodeExpiration());
        DeviceAuthorizationCode authorizationCode = oauthIdentityService.findByProviderAndProviderUserId(
                oauthUser.getProvider(),
                oauthUser.getExternalId()
        )
                .map(
                        identity -> DeviceAuthorizationCode.forMember(
                                codeHash,
                                codeChallenge,
                                identity.getMemberId(),
                                now,
                                expiresAt
                        )
                )
                .orElseGet(
                        () -> DeviceAuthorizationCode.forOAuthUser(
                                codeHash,
                                codeChallenge,
                                oauthUser,
                                now,
                                expiresAt
                        )
                );
        codeRepository.save(authorizationCode);
        return code;
    }

    /**
     * 코드를 토큰으로 바꾼다. 만료·재사용·verifier 불일치는 모두 같은 400 `DEVICE_CODE_INVALID`다. 이미 쓴 코드가 다시 오면
     * 그 코드로 만든 세션을 폐기한다(기획서 5.2 계약 표).
     */
    @Transactional(noRollbackFor = AuthException.class)
    public DeviceTokenResult exchange(ExchangeDeviceCodeCommand command) {
        if (command == null || command.code() == null || command.code()
                .isBlank() || command.device() == null) {
            throw new AuthException(AuthErrorCode.DEVICE_CODE_INVALID);
        }
        Instant now = Instant.now(clock);
        DeviceAuthorizationCode code = codeRepository.findByCodeHashForUpdate(OpaqueToken.hash(command.code()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.DEVICE_CODE_INVALID));

        if (code.isConsumed()) {
            revokeSessionIfPresent(
                    code.getConsumedSessionId(),
                    now,
                    "디바이스 코드 재사용 감지"
            );
            throw new AuthException(AuthErrorCode.DEVICE_CODE_INVALID);
        }
        if (code.isExpiredAt(now) || !code.matchesVerifier(command.codeVerifier())) {
            throw new AuthException(AuthErrorCode.DEVICE_CODE_INVALID);
        }

        if (code.requiresNickname()) {
            code.consume(
                    now,
                    null
            );
            codeRepository.save(code);
            return DeviceTokenResult.nicknameSetupRequired(
                    authTokenProvider.issueNickname(code.toOAuthUser()),
                    jwtProperties.getNicknameTokenExpiration()
            );
        }

        Member member = memberService.findById(code.getMemberId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.DEVICE_CODE_INVALID));
        DeviceSession session = sessionRepository.save(
                DeviceSession.create(
                        member.getId(),
                        command.device(),
                        now
                )
        );
        code.consume(
                now,
                session.getId()
        );
        codeRepository.save(code);
        return issueTokens(
                member,
                session,
                now
        );
    }

    /**
     * 리프레시 토큰을 새 쌍으로 바꾼다(rotation). 이미 바꾼 토큰이 다시 오면 탈취로 보고 세션을 통째로 폐기한다(RFC 9700
     * §4.14.2). 어느 경우든 401 `REFRESH_TOKEN_INVALID` 하나로 답해 어떤 단계에서 걸렸는지 드러내지 않는다.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public DeviceTokenResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        Instant now = Instant.now(clock);
        DeviceRefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(OpaqueToken.hash(refreshToken))
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        if (current.isRotated()) {
            revokeSessionIfPresent(
                    current.getSessionId(),
                    now,
                    "리프레시 토큰 재사용 감지"
            );
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        DeviceSession session = sessionRepository.findById(current.getSessionId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID));
        if (!session.isActive()) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (current.isExpiredAt(now)) {
            session.revoke(now);
            sessionRepository.save(session);
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        Member member = memberService.findById(session.getMemberId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        current.rotate(now);
        refreshTokenRepository.save(current);
        session.touch(now);
        sessionRepository.save(session);
        return issueTokens(
                member,
                session,
                now
        );
    }

    /** RFC 7009: 모르는 토큰이어도 성공으로 답한다 */
    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        Instant now = Instant.now(clock);
        refreshTokenRepository.findByTokenHashForUpdate(OpaqueToken.hash(refreshToken))
                .ifPresent(
                        token -> revokeSessionIfPresent(
                                token.getSessionId(),
                                now,
                                "리프레시 토큰으로 폐기"
                        )
                );
    }

    /** 현재 액세스 토큰의 세션(`sid`)을 폐기한다. 로그아웃·`revoke` 본문 없는 호출에 쓴다 */
    @Transactional
    public void revokeSession(
            long sessionId,
            long memberId
    ) {
        Instant now = Instant.now(clock);
        sessionRepository.findByIdAndMemberId(
                sessionId,
                memberId
        )
                .ifPresent(session -> {
                    session.revoke(now);
                    sessionRepository.save(session);
                });
    }

    @Transactional(readOnly = true)
    public List<DeviceSessionResult> findSessions(AuthenticatedMember member) {
        if (member == null) {
            throw new AuthException(AuthErrorCode.UNAUTHENTICATED);
        }
        Instant now = Instant.now(clock);
        return sessionRepository.findActiveByMemberId(
                member.getMemberId(),
                now
        )
                .stream()
                .map(
                        session -> DeviceSessionResult.from(
                                session,
                                member.getDeviceSessionId()
                        )
                )
                .toList();
    }

    /** 본인 세션이 아니면 존재를 알리지 않고 404 */
    @Transactional
    public void deleteSession(
            long sessionId,
            long memberId
    ) {
        Instant now = Instant.now(clock);
        DeviceSession session = sessionRepository.findByIdAndMemberId(
                sessionId,
                memberId
        )
                .filter(DeviceSession::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.DEVICE_SESSION_NOT_FOUND));
        session.revoke(now);
        sessionRepository.save(session);
    }

    /** `JwtAuthenticationFilter`가 `DEVICE_ACCESS` 토큰마다 부른다. 폐기된 세션의 토큰은 만료 전이라도 401이 된다 */
    @Transactional(readOnly = true)
    public boolean isSessionActive(
            long sessionId,
            long memberId
    ) {
        return sessionRepository.existsActive(
                sessionId,
                memberId
        );
    }

    private DeviceTokenResult issueTokens(
            Member member,
            DeviceSession session,
            Instant now
    ) {
        String refreshToken = OpaqueToken.generate(secureRandom);
        refreshTokenRepository.save(
                DeviceRefreshToken.issue(
                        session.getId(),
                        OpaqueToken.hash(refreshToken),
                        now,
                        now.plus(deviceProperties.getRefreshTokenExpiration())
                )
        );
        AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );
        String accessToken = authTokenProvider.issueDevice(
                authenticatedMember,
                session.getId()
        );
        return DeviceTokenResult.authenticated(
                accessToken,
                refreshToken,
                jwtProperties.getExpiration(),
                session.getId(),
                session.getDeviceName()
        );
    }

    private void revokeSessionIfPresent(
            Long sessionId,
            Instant now,
            String reason
    ) {
        if (sessionId == null) {
            return;
        }
        Optional<DeviceSession> session = sessionRepository.findById(sessionId);
        session.ifPresent(found -> {
            if (found.isActive()) {
                log.warn(
                        "{}: sessionId={}",
                        reason,
                        sessionId
                );
            }
            found.revoke(now);
            sessionRepository.save(found);
        });
    }

    private static void validateProperties(DeviceAuthProperties properties) {
        if (properties == null || isNotPositive(properties.getCodeExpiration())
                || isNotPositive(properties.getRefreshTokenExpiration())) {
            throw new AuthException(AuthErrorCode.JWT_CONFIGURATION_INVALID);
        }
    }

    private static boolean isNotPositive(Duration duration) {
        return duration == null || duration.isZero() || duration.isNegative();
    }
}

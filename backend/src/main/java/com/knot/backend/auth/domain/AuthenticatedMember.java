package com.knot.backend.auth.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 액세스 토큰이 가리키는 회원.
 *
 * 데스크톱 2단계 인증(기획서 5.2)의 `DEVICE_ACCESS` 토큰은 기기 세션 id(`sid`)를 함께 싣는다. 웹 1단계 `ACCESS` 토큰에는
 * 세션이 없으므로 `deviceSessionId`가 null이다. 화면에 보이는 회원 정보는 둘이 같다.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class AuthenticatedMember {
    private static final int MAX_NICKNAME_LENGTH = 20;
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 500;

    private final long memberId;
    private final String nickname;
    private final String profileImageUrl;
    private final Long deviceSessionId;

    private AuthenticatedMember(
            long memberId,
            String nickname,
            String profileImageUrl,
            Long deviceSessionId
    ) {
        if (memberId <= 0) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        if (nickname == null || nickname.isBlank() || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        if (profileImageUrl != null
                && (profileImageUrl.isBlank() || profileImageUrl.length() > MAX_PROFILE_IMAGE_URL_LENGTH)) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        if (deviceSessionId != null && deviceSessionId <= 0) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        this.memberId = memberId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.deviceSessionId = deviceSessionId;
    }

    public static AuthenticatedMember of(
            long memberId,
            String nickname,
            String profileImageUrl
    ) {
        return new AuthenticatedMember(
                memberId,
                nickname,
                profileImageUrl,
                null
        );
    }

    /** 디바이스 액세스 토큰(`sid` 있음)의 주체 */
    public static AuthenticatedMember ofDeviceSession(
            long memberId,
            String nickname,
            String profileImageUrl,
            long deviceSessionId
    ) {
        return new AuthenticatedMember(
                memberId,
                nickname,
                profileImageUrl,
                deviceSessionId
        );
    }

    public boolean isDeviceSession() {
        return deviceSessionId != null;
    }
}

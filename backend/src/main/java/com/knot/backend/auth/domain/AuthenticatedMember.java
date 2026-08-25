package com.knot.backend.auth.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public final class AuthenticatedMember {
    private static final int MAX_NICKNAME_LENGTH = 39;
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 500;
    private final long memberId;
    private final long githubId;
    private final String nickname;
    private final String profileImageUrl;

    private AuthenticatedMember(
            long memberId,
            long githubId,
            String nickname,
            String profileImageUrl
    ) {
        if (memberId <= 0) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        if (githubId <= 0) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        if (nickname == null || nickname.isBlank() || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        if (profileImageUrl != null && (profileImageUrl.isBlank()
                || profileImageUrl.length() > MAX_PROFILE_IMAGE_URL_LENGTH)) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }
        this.memberId = memberId;
        this.githubId = githubId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public static AuthenticatedMember of(
            long memberId,
            long githubId,
            String nickname,
            String profileImageUrl
    ) {
        return new AuthenticatedMember(
                memberId,
                githubId,
                nickname,
                profileImageUrl
        );
    }
}

package com.knot.backend.auth.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public final class OAuthUser {

    private static final int MAX_NICKNAME_LENGTH = 20;
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 500;

    private final long externalId;
    private final String nickname;
    private final String profileImageUrl;

    private OAuthUser(
            long externalId,
            String nickname,
            String profileImageUrl
    ) {
        if (externalId <= 0) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }
        if (nickname == null || nickname.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }
        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }
        if (profileImageUrl != null && (profileImageUrl.isBlank()
                || profileImageUrl.length() > MAX_PROFILE_IMAGE_URL_LENGTH)) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }
        this.externalId = externalId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public static OAuthUser of(
            long externalId,
            String nickname,
            String profileImageUrl
    ) {
        return new OAuthUser(
                externalId,
                nickname,
                profileImageUrl
        );
    }
}

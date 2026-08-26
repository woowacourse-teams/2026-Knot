package com.knot.backend.auth.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public final class OAuthUser {
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 500;

    private final OAuthProvider provider;
    private final String externalId;
    private final String profileImageUrl;

    private OAuthUser(
            OAuthProvider provider,
            String externalId,
            String profileImageUrl
    ) {
        validate(
                provider,
                externalId,
                profileImageUrl
        );

        this.provider = provider;
        this.externalId = externalId;
        this.profileImageUrl = profileImageUrl;
    }

    public static OAuthUser of(
            OAuthProvider provider,
            String externalId,
            String profileImageUrl
    ) {
        return new OAuthUser(
                provider,
                externalId,
                profileImageUrl
        );
    }

    private static void validate(
            OAuthProvider provider,
            String externalId,
            String profileImageUrl
    ) {
        if (provider == null || externalId == null || externalId.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }

        if (profileImageUrl != null && (profileImageUrl.isBlank()
                || profileImageUrl.length() > MAX_PROFILE_IMAGE_URL_LENGTH)) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }
    }
}

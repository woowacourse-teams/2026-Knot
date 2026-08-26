package com.knot.backend.auth.application;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import lombok.Getter;

@Getter
public final class AuthLoginResult {
    private final String token;
    private final boolean nicknameRequired;

    private AuthLoginResult(
            String token,
            boolean nicknameRequired
    ) {
        if (token == null || token.isBlank()) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }

        this.token = token;
        this.nicknameRequired = nicknameRequired;
    }

    public static AuthLoginResult authenticated(String accessToken) {
        return new AuthLoginResult(
                accessToken,
                false
        );
    }

    public static AuthLoginResult nickname(String nicknameToken) {
        return new AuthLoginResult(
                nicknameToken,
                true
        );
    }

    public boolean requiresNickname() {
        return nicknameRequired;
    }
}

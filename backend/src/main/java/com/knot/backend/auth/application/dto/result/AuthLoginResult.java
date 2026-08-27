package com.knot.backend.auth.application.dto.result;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;

public record AuthLoginResult(
        String token,
        boolean requiresNickname
) {
    public AuthLoginResult {
        if (token == null || token.isBlank()) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
    }

    public static AuthLoginResult authenticated(String accessToken) {
        return new AuthLoginResult(
                accessToken,
                false
        );
    }

    public static AuthLoginResult nicknameSetupRequired(String nicknameToken) {
        return new AuthLoginResult(
                nicknameToken,
                true
        );
    }
}

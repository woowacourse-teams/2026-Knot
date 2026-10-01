package com.knot.backend.auth.application.dto.result;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import java.time.Duration;

public record AuthLoginResult(
        String token,
        String refreshToken,
        Duration refreshMaxAge,
        boolean requiresNickname
) {
    public AuthLoginResult {
        if (token == null || token.isBlank()) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (requiresNickname && (refreshToken != null || refreshMaxAge != null)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        if (!requiresNickname && (refreshToken == null || refreshToken.isBlank() || refreshMaxAge == null
                || refreshMaxAge.isNegative() || refreshMaxAge.isZero())) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
    }

    public static AuthLoginResult authenticated(String accessToken, String refreshToken, Duration refreshMaxAge) {
        return new AuthLoginResult(
                accessToken,
                refreshToken,
                refreshMaxAge,
                false
        );
    }

    public static AuthLoginResult nicknameSetupRequired(String nicknameToken) {
        return new AuthLoginResult(
                nicknameToken,
                null,
                null,
                true
        );
    }

    @Override
    public String toString() {
        return "AuthLoginResult[requiresNickname=" + requiresNickname + "]";
    }
}

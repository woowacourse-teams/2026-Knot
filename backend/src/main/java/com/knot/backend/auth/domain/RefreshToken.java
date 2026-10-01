package com.knot.backend.auth.domain;

import lombok.Getter;

@Getter
public final class RefreshToken {
    private final String value;
    private final String hash;

    private RefreshToken(String value, String hash) {
        if (value == null || value.isBlank() || hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        this.value = value;
        this.hash = hash;
    }

    public static RefreshToken of(String value, String hash) {
        return new RefreshToken(value, hash);
    }
}

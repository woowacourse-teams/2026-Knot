package com.knot.backend.auth.domain;

public interface RefreshTokenProvider {
    RefreshToken issue();

    String hash(String value);
}

package com.knot.backend.auth.domain;

import java.time.Instant;

public interface RefreshTokenProvider {

    RefreshToken issue(Instant expiresAt);

    RefreshToken identify(String value);

    void validate(RefreshToken token);
}

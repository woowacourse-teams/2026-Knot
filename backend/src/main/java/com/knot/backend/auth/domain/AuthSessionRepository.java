package com.knot.backend.auth.domain;

import java.util.Optional;

public interface AuthSessionRepository {
    AuthSession save(AuthSession session);

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);
}

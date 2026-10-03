package com.knot.backend.auth.domain;

public interface AuthSessionRepository {
    AuthSession save(AuthSession session);
}

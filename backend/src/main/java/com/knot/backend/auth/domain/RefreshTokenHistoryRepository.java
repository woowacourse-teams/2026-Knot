package com.knot.backend.auth.domain;

import java.util.Optional;

public interface RefreshTokenHistoryRepository {
    RefreshTokenHistory save(RefreshTokenHistory history);

    Optional<RefreshTokenHistory> findByRefreshTokenHash(String refreshTokenHash);
}

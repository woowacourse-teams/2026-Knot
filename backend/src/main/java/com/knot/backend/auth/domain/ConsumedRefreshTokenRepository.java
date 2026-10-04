package com.knot.backend.auth.domain;

import java.util.Optional;

public interface ConsumedRefreshTokenRepository {
    ConsumedRefreshToken save(ConsumedRefreshToken consumedRefreshToken);

    Optional<ConsumedRefreshToken> findByRefreshTokenHash(String refreshTokenHash);
}

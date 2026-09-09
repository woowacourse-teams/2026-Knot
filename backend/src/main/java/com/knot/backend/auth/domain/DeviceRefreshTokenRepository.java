package com.knot.backend.auth.domain;

import java.util.Optional;

public interface DeviceRefreshTokenRepository {

    DeviceRefreshToken save(DeviceRefreshToken token);

    /** rotation·재사용 판정을 직렬화하려고 행 잠금으로 읽는다 */
    Optional<DeviceRefreshToken> findByTokenHashForUpdate(String tokenHash);
}

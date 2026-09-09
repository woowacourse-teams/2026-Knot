package com.knot.backend.auth.domain;

import java.time.Instant;
import java.util.Optional;

public interface DeviceAuthorizationCodeRepository {

    DeviceAuthorizationCode save(DeviceAuthorizationCode code);

    /** 교환·재사용 판정을 직렬화하려고 행 잠금으로 읽는다 */
    Optional<DeviceAuthorizationCode> findByCodeHashForUpdate(String codeHash);

    /** 만료된 지 오래된 코드를 지운다. 재사용 감지를 위해 만료 직후에는 남겨 둔다 */
    int deleteExpiredBefore(Instant threshold);
}

package com.knot.backend.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceSessionRepository {

    DeviceSession save(DeviceSession session);

    Optional<DeviceSession> findById(long sessionId);

    Optional<DeviceSession> findByIdAndMemberId(
            long sessionId,
            long memberId
    );

    /** 폐기되지 않았고 현재 리프레시 토큰이 아직 만료되지 않은 세션. 최근 사용 순 */
    List<DeviceSession> findActiveByMemberId(
            long memberId,
            Instant now
    );

    /** 액세스 토큰 `sid` 검증용. 폐기 여부와 회원 일치만 본다(액세스 토큰은 1시간 안에 만료된다) */
    boolean existsActive(
            long sessionId,
            long memberId
    );
}

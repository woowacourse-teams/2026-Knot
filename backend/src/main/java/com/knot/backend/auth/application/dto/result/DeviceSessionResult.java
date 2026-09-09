package com.knot.backend.auth.application.dto.result;

import com.knot.backend.auth.domain.DeviceSession;
import java.time.Instant;

/** 기기 목록 한 줄(기획서 5.2 `GET /auth/sessions`). `current`는 요청한 액세스 토큰의 `sid`와 같은지다 */
public record DeviceSessionResult(
        long id,
        String deviceName,
        String platform,
        String appVersion,
        Instant createdAt,
        Instant lastUsedAt,
        boolean current
) {

    public static DeviceSessionResult from(
            DeviceSession session,
            Long currentSessionId
    ) {
        return new DeviceSessionResult(
                session.getId(),
                session.getDeviceName(),
                session.getPlatform(),
                session.getAppVersion(),
                session.getCreatedAt(),
                session.getLastUsedAt(),
                currentSessionId != null && currentSessionId.equals(session.getId())
        );
    }
}

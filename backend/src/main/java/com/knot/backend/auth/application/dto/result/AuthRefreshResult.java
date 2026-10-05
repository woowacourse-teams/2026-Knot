package com.knot.backend.auth.application.dto.result;

import java.time.Duration;

public record AuthRefreshResult(
        String accessToken,
        String refreshToken,
        Duration refreshMaxAge
) {
}

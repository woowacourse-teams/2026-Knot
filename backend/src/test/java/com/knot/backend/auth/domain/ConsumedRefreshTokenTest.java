package com.knot.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsumedRefreshTokenTest {
    private static final Instant CONSUMED_AT = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    @DisplayName("소비한 refresh 토큰의 해시와 세션 식별자를 기록한다")
    void create_success_consumedToken() {
        // given
        String refreshTokenHash = "a".repeat(64);

        // when
        ConsumedRefreshToken consumedRefreshToken = ConsumedRefreshToken.create(
                1L,
                refreshTokenHash,
                CONSUMED_AT
        );

        // then
        assertThat(consumedRefreshToken.getAuthSessionId()).isEqualTo(1L);
        assertThat(consumedRefreshToken.getRefreshTokenHash()).isEqualTo(refreshTokenHash);
        assertThat(consumedRefreshToken.getConsumedAt()).isEqualTo(CONSUMED_AT);
    }

    @Test
    @DisplayName("세션 ID가 양수가 아니면 소비한 refresh 토큰을 만들 수 없다")
    void create_failure_invalidSessionId() {
        // when & then
        assertThatThrownBy(
                () -> ConsumedRefreshToken.create(
                        0L,
                        "a".repeat(64),
                        CONSUMED_AT
                )
        ).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("SHA-256 형식이 아닌 해시는 소비한 refresh 토큰으로 저장할 수 없다")
    void create_failure_invalidHash() {
        // when & then
        assertThatThrownBy(
                () -> ConsumedRefreshToken.create(
                        1L,
                        "not-a-hash",
                        CONSUMED_AT
                )
        ).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("소비 시각이 없으면 소비한 refresh 토큰을 만들 수 없다")
    void create_failure_missingConsumedAt() {
        // when & then
        assertThatThrownBy(
                () -> ConsumedRefreshToken.create(
                        1L,
                        "a".repeat(64),
                        null
                )
        ).isInstanceOf(AuthException.class);
    }
}

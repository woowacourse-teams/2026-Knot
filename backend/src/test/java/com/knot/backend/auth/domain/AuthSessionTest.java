package com.knot.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthSessionTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("로그인 세션은 refresh 해시와 7일 비활성·30일 절대 만료를 보존한다")
    void create_success() {
        // when
        AuthSession session = AuthSession.create(1L, HASH, NOW);

        // then
        assertThat(session.getMemberId()).isEqualTo(1L);
        assertThat(session.getRefreshTokenHash()).isEqualTo(HASH);
        assertThat(session.getCreatedAt()).isEqualTo(NOW);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(session.getAbsoluteExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(session.getRevokedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("양수가 아닌 회원 ID로는 로그인 세션을 생성할 수 없다")
    void create_failure_invalidMemberId(long memberId) {
        // when & then
        assertThatThrownBy(() -> AuthSession.create(memberId, HASH, NOW)).isInstanceOf(AuthException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "raw-refresh-token"})
    @DisplayName("해시가 아닌 값으로는 로그인 세션을 생성할 수 없다")
    void create_failure_invalidHash(String hash) {
        // when & then
        assertThatThrownBy(() -> AuthSession.create(1L, hash, NOW)).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("발급 시각이 없으면 로그인 세션을 생성할 수 없다")
    void create_failure_missingCreatedAt() {
        // when & then
        assertThatThrownBy(() -> AuthSession.create(1L, HASH, null)).isInstanceOf(AuthException.class);
    }
}

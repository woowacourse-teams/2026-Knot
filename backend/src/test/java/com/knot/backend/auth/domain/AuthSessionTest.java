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

    @Test
    @DisplayName("로그인 세션은 만료 직전까지 활성 상태다")
    void isActive_success_beforeExpiry() {
        // given
        AuthSession session = AuthSession.create(1L, HASH, NOW);

        // when & then
        assertThat(session.isActive(NOW.plus(Duration.ofDays(7)).minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("만료 시각에 도달한 로그인 세션은 사용할 수 없다")
    void isActive_failure_expired() {
        // given
        AuthSession session = AuthSession.create(1L, HASH, NOW);

        // when & then
        assertThat(session.isActive(NOW.plus(Duration.ofDays(7)))).isFalse();
    }

    @Test
    @DisplayName("발급 이전 시각이나 시각 누락은 활성 세션으로 판단하지 않는다")
    void isActive_failure_invalidTime() {
        // given
        AuthSession session = AuthSession.create(1L, HASH, NOW);

        // when & then
        assertThat(session.isActive(NOW.minusSeconds(1))).isFalse();
        assertThat(session.isActive(null)).isFalse();
    }

    @Test
    @DisplayName("refresh 쿠키 수명은 로그인 세션에서 남은 시간만 반환한다")
    void remainingRefreshLifetime_success() {
        // given
        AuthSession session = AuthSession.create(1L, HASH, NOW);

        // when & then
        assertThat(session.remainingRefreshLifetime(NOW)).isEqualTo(Duration.ofDays(7));
        assertThat(session.remainingRefreshLifetime(NOW.plus(Duration.ofDays(6)))).isEqualTo(Duration.ofDays(1));
    }

    @Test
    @DisplayName("이미 만료되거나 유효하지 않은 시각에는 refresh 쿠키 수명이 없다")
    void remainingRefreshLifetime_failure_expired() {
        // given
        AuthSession session = AuthSession.create(1L, HASH, NOW);

        // when & then
        assertThat(session.remainingRefreshLifetime(NOW.plus(Duration.ofDays(7)))).isEqualTo(Duration.ZERO);
        assertThat(session.remainingRefreshLifetime(null)).isEqualTo(Duration.ZERO);
    }
}

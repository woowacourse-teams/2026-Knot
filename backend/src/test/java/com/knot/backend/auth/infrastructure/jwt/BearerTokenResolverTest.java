package com.knot.backend.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BearerTokenResolverTest {

    @Test
    @DisplayName("Bearer 스킴 뒤의 토큰만 돌려준다")
    void resolve_success() {
        // when
        String token = BearerTokenResolver.resolve("Bearer jwt-token");

        // then
        assertThat(token).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("스킴 이름의 대소문자는 가리지 않는다")
    void resolve_success_caseInsensitiveScheme() {
        // when
        String token = BearerTokenResolver.resolve("bearer jwt-token");

        // then
        assertThat(token).isEqualTo("jwt-token");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "jwt-token", "Basic jwt-token", "Bearer", "Bearer   "})
    @DisplayName("헤더가 없거나 Bearer 토큰을 담고 있지 않으면 null을 돌려준다")
    void resolve_failure(String header) {
        // when
        String token = BearerTokenResolver.resolve(header);

        // then
        assertThat(token).isNull();
    }
}

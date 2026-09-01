package com.knot.backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActiveChatStreamRegistryTest {

    @Test
    @DisplayName("같은 세션은 동시에 하나의 스트림만 획득할 수 있다")
    void acquire_failure_duplicateSession() {
        // given
        ActiveChatStreamRegistry registry = new ActiveChatStreamRegistry();

        // when
        boolean first = registry.tryAcquire(1L);
        boolean second = registry.tryAcquire(1L);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("스트림을 해제하면 같은 세션이 다시 스트림을 획득할 수 있다")
    void release_success() {
        // given
        ActiveChatStreamRegistry registry = new ActiveChatStreamRegistry();
        registry.tryAcquire(1L);

        // when
        registry.release(1L);

        // then
        assertThat(registry.tryAcquire(1L)).isTrue();
    }
}

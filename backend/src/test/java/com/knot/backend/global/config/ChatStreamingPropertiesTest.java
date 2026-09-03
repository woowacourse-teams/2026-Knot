package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatStreamingPropertiesTest {

    @Test
    @DisplayName("양수 timeout을 밀리초로 변환한다")
    void timeoutMillis_success() {
        // given
        ChatStreamingProperties properties = new ChatStreamingProperties();
        properties.setTimeout(Duration.ofSeconds(90));

        // when & then
        assertThat(properties.timeoutMillis()).isEqualTo(90_000L);
    }

    @Test
    @DisplayName("0 이하 timeout은 거부한다")
    void timeoutMillis_failure_nonPositive() {
        // given
        ChatStreamingProperties properties = new ChatStreamingProperties();
        properties.setTimeout(Duration.ZERO);

        // when & then
        assertThatThrownBy(properties::timeoutMillis).isInstanceOf(IllegalStateException.class);
    }
}

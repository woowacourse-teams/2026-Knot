package com.knot.backend.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmUsageTest {

    @Test
    @DisplayName("모델과 토큰 사용량을 그대로 담는다")
    void of_success() {
        // given

        // when
        LlmUsage usage = LlmUsage.of(
                "claude-opus-5",
                25L,
                12L,
                8L,
                3L
        );

        // then
        assertThat(usage.getModel()).isEqualTo("claude-opus-5");
        assertThat(usage.getInputTokens()).isEqualTo(25L);
        assertThat(usage.getOutputTokens()).isEqualTo(12L);
        assertThat(usage.getCacheReadInputTokens()).isEqualTo(8L);
        assertThat(usage.getCacheCreationInputTokens()).isEqualTo(3L);
    }

    @Test
    @DisplayName("사용량은 부수 기록이므로 음수가 와도 답변 저장을 막지 않고 0으로 담는다")
    void of_success_clampsNegativeTokens() {
        // given

        // when
        LlmUsage usage = LlmUsage.of(
                "claude-opus-5",
                -1L,
                -2L,
                -3L,
                -4L
        );

        // then
        assertThat(usage.getInputTokens()).isZero();
        assertThat(usage.getOutputTokens()).isZero();
        assertThat(usage.getCacheReadInputTokens()).isZero();
        assertThat(usage.getCacheCreationInputTokens()).isZero();
    }

    @Test
    @DisplayName("모델 이름이 컬럼 길이를 넘으면 잘라서 담는다")
    void of_success_truncatesLongModel() {
        // given
        String longModel = "m".repeat(LlmUsage.MODEL_MAX_LENGTH + 20);

        // when
        LlmUsage usage = LlmUsage.of(
                longModel,
                1L,
                1L,
                0L,
                0L
        );

        // then
        assertThat(usage.getModel()).hasSize(LlmUsage.MODEL_MAX_LENGTH);
    }

    @Test
    @DisplayName("모델 이름이 없거나 공백이면 null로 담는다")
    void of_success_blankModelBecomesNull() {
        // given

        // when
        LlmUsage blank = LlmUsage.of(
                " ",
                1L,
                1L,
                0L,
                0L
        );
        LlmUsage missing = LlmUsage.of(
                null,
                1L,
                1L,
                0L,
                0L
        );

        // then
        assertThat(blank.getModel()).isNull();
        assertThat(missing.getModel()).isNull();
    }
}

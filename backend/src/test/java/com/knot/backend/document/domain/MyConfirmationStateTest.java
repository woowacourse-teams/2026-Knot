package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MyConfirmationStateTest {
    @Test
    @DisplayName("확인 대상이 아닌 멤버는 NOT_REQUIRED다")
    void resolve_success_notRequired() {
        // when & then
        assertThat(
                MyConfirmationState.resolve(
                        false,
                        null
                )
        ).isEqualTo(MyConfirmationState.NOT_REQUIRED);
    }

    @Test
    @DisplayName("확인 대상이지만 확인 시각이 없으면 PENDING이다")
    void resolve_success_pending() {
        // when & then
        assertThat(
                MyConfirmationState.resolve(
                        true,
                        null
                )
        ).isEqualTo(MyConfirmationState.PENDING);
    }

    @Test
    @DisplayName("확인 시각이 있는 대상은 CONFIRMED다")
    void resolve_success_confirmed() {
        // when & then
        assertThat(
                MyConfirmationState.resolve(
                        true,
                        Instant.parse("2026-10-06T00:00:00Z")
                )
        ).isEqualTo(MyConfirmationState.CONFIRMED);
    }
}

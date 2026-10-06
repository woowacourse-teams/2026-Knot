package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentConfirmationStateTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("확인한 대상은 활성 여부와 관계없이 CONFIRMED다")
    void resolve_success_confirmed(boolean active) {
        // when & then
        assertThat(
                DocumentConfirmationState.resolve(
                        active,
                        Instant.parse("2026-10-06T00:00:00Z")
                )
        ).isEqualTo(DocumentConfirmationState.CONFIRMED);
    }

    @Test
    @DisplayName("활성 미확인 대상은 PENDING이다")
    void resolve_success_pending() {
        // when & then
        assertThat(
                DocumentConfirmationState.resolve(
                        true,
                        null
                )
        ).isEqualTo(DocumentConfirmationState.PENDING);
    }

    @Test
    @DisplayName("탈퇴한 미확인 대상은 EXCLUDED다")
    void resolve_success_excluded() {
        // when & then
        assertThat(
                DocumentConfirmationState.resolve(
                        false,
                        null
                )
        ).isEqualTo(DocumentConfirmationState.EXCLUDED);
    }
}

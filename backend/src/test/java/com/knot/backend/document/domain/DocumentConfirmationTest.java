package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentConfirmationTest {
    @Test
    @DisplayName("확인 대상의 최초 확인 시각을 기록한다")
    void confirm_success() {
        // given
        DocumentConfirmation confirmation = DocumentConfirmation.require(
                1,
                2
        );
        Instant confirmedAt = Instant.parse("2026-10-06T00:01:00Z");

        // when
        confirmation.confirm(confirmedAt);

        // then
        assertThat(confirmation.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    @DisplayName("반복 확인은 최초 시각을 유지한다")
    void confirm_success_repeatedRequest() {
        // given
        DocumentConfirmation confirmation = DocumentConfirmation.require(
                1,
                2
        );
        Instant confirmedAt = Instant.parse("2026-10-06T00:01:00Z");
        confirmation.confirm(confirmedAt);

        // when
        confirmation.confirm(confirmedAt.plusSeconds(60));

        // then
        assertThat(confirmation.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    @DisplayName("최초 확인 시각이 없으면 저장하지 않는다")
    void confirm_failure_missingTime() {
        // given
        DocumentConfirmation confirmation = DocumentConfirmation.require(
                1,
                2
        );

        // when & then
        assertThatThrownBy(() -> confirmation.confirm(null)).isInstanceOf(DocumentException.class);
        assertThat(confirmation.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("고정 확인 대상은 문서·멤버 복합 키와 미확인 상태로 생성한다")
    void require_success() {
        // when
        DocumentConfirmation confirmation = DocumentConfirmation.require(
                1,
                2
        );

        // then
        assertThat(confirmation.getId()).isEqualTo(
                DocumentConfirmationId.of(
                        1,
                        2
                )
        );
        assertThat(confirmation.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("잘못된 멤버 ID의 확인 대상은 생성하지 못한다")
    void require_failure_invalidMember() {
        // when & then
        assertThatThrownBy(
                () -> DocumentConfirmation.require(
                        1,
                        0
                )
        ).isInstanceOf(DocumentException.class);
    }
}

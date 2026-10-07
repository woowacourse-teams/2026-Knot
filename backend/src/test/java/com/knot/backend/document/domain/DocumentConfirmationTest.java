package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentConfirmationTest {
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

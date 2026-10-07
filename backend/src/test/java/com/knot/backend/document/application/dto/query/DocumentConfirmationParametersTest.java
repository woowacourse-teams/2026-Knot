package com.knot.backend.document.application.dto.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.domain.DocumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentConfirmationParametersTest {
    @Test
    @DisplayName("크기와 커서를 생략하면 첫 50명을 조회한다")
    void of_success_defaults() {
        // when
        DocumentConfirmationParameters parameters = DocumentConfirmationParameters.of(
                null,
                null
        );
        // then
        assertThat(parameters.size()).isEqualTo(50);
        assertThat(parameters.cursor()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    @DisplayName("허용 크기 양쪽 경계와 지정한 커서를 유지한다")
    void of_success_boundaries(int size) {
        // when
        DocumentConfirmationParameters parameters = DocumentConfirmationParameters.of(
                "cursor",
                size
        );
        // then
        assertThat(parameters.size()).isEqualTo(size);
        assertThat(parameters.cursor()).isEqualTo("cursor");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101, Integer.MAX_VALUE})
    @DisplayName("범위를 벗어난 크기는 INVALID_PARAMETER다")
    void of_failure_invalidSize(int size) {
        // when & then
        assertThatThrownBy(
                () -> DocumentConfirmationParameters.of(
                        null,
                        size
                )
        ).isInstanceOf(DocumentException.class)
                .extracting(
                        error -> ((DocumentException) error).getErrorCode()
                                .getCode()
                )
                .isEqualTo("INVALID_PARAMETER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("제공했지만 비어 있는 커서는 거절한다")
    void of_failure_blankCursor(String cursor) {
        // when & then
        assertThatThrownBy(
                () -> DocumentConfirmationParameters.of(
                        cursor,
                        50
                )
        ).isInstanceOf(DocumentException.class);
    }
}

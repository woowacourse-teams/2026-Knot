package com.knot.backend.document.application.dto.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.domain.DocumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentGenerationJobListParametersTest {

    @Test
    @DisplayName("생략한 페이지 크기는 20이다")
    void of_success_defaultSize() {
        // when
        DocumentGenerationJobListParameters parameters = DocumentGenerationJobListParameters.of(
                null,
                null
        );

        // then
        assertThat(parameters.size()).isEqualTo(20);
        assertThat(parameters.cursor()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    @DisplayName("1부터 100까지의 페이지 크기를 허용한다")
    void of_success_validSize(int size) {
        // when & then
        assertThat(
                DocumentGenerationJobListParameters.of(
                        "cursor",
                        size
                )
                        .size()
        ).isEqualTo(size);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, Integer.MAX_VALUE})
    @DisplayName("범위를 벗어난 페이지 크기는 거절한다")
    void of_failure_invalidSize(int size) {
        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationJobListParameters.of(
                        null,
                        size
                )
        ).isInstanceOf(DocumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("빈 커서는 거절한다")
    void of_failure_blankCursor(String cursor) {
        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationJobListParameters.of(
                        cursor,
                        20
                )
        ).isInstanceOf(DocumentException.class);
    }
}

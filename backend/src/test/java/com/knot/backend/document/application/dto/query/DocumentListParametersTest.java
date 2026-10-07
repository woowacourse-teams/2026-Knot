package com.knot.backend.document.application.dto.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.MyConfirmationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentListParametersTest {
    @Test
    @DisplayName("생략한 페이지 크기는 50이며 필터는 제한하지 않는다")
    void of_success_defaults() {
        // when
        DocumentListParameters parameters = DocumentListParameters.of(
                null,
                null,
                null,
                null
        );
        // then
        assertThat(parameters.size()).isEqualTo(50);
        assertThat(parameters.cursor()).isNull();
        assertThat(parameters.myConfirmation()).isNull();
        assertThat(parameters.recordingSessionId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    @DisplayName("페이지 크기 경계와 두 필터를 유지한다")
    void of_success_boundaries(int size) {
        // when
        DocumentListParameters parameters = DocumentListParameters.of(
                null,
                size,
                MyConfirmationState.PENDING,
                42L
        );
        // then
        assertThat(parameters.size()).isEqualTo(size);
        assertThat(parameters.myConfirmation()).isEqualTo(MyConfirmationState.PENDING);
        assertThat(parameters.recordingSessionId()).isEqualTo(42L);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101, Integer.MAX_VALUE})
    @DisplayName("범위를 벗어난 페이지 크기를 거절한다")
    void of_failure_invalidSize(int size) {
        // when & then
        assertThatThrownBy(
                () -> DocumentListParameters.of(
                        null,
                        size,
                        null,
                        null
                )
        ).isInstanceOf(DocumentException.class)
                .satisfies(
                        error -> assertThat(
                                ((DocumentException) error).getErrorCode()
                                        .getCode()
                        ).isEqualTo("INVALID_PARAMETER")
                );
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    @DisplayName("양수가 아닌 녹음 ID를 거절한다")
    void of_failure_invalidRecordingId(long recordingId) {
        // when & then
        assertThatThrownBy(
                () -> DocumentListParameters.of(
                        null,
                        null,
                        null,
                        recordingId
                )
        ).isInstanceOf(DocumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("전달했지만 비어 있는 커서를 거절한다")
    void of_failure_blankCursor(String cursor) {
        // when & then
        assertThatThrownBy(
                () -> DocumentListParameters.of(
                        cursor,
                        null,
                        null,
                        null
                )
        ).isInstanceOf(DocumentException.class);
    }
}

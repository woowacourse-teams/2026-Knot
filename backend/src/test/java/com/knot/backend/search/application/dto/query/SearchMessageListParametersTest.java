package com.knot.backend.search.application.dto.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchMessageListParametersTest {

    @Test
    @DisplayName("입력을 생략하면 최신 30개를 요청한다")
    void ofDefaults_success() {
        // when
        SearchMessageListParameters parameters = SearchMessageListParameters.of(
                null,
                null
        );
        // then
        assertThat(parameters.size()).isEqualTo(30);
        assertThat(parameters.beforeSequence()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 100})
    @DisplayName("허용된 크기와 양의 순서 경계를 유지한다")
    void ofBoundary_success(int size) {
        // when
        SearchMessageListParameters parameters = SearchMessageListParameters.of(
                Integer.MAX_VALUE,
                size
        );
        // then
        assertThat(parameters.size()).isEqualTo(size);
        assertThat(parameters.beforeSequence()).isEqualTo(Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101, Integer.MAX_VALUE})
    @DisplayName("크기 범위를 벗어나면 INVALID_PARAMETER다")
    void ofSize_failure_outOfRange(int size) {
        // when & then
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> SearchMessageListParameters.of(
                        null,
                        size
                )
        )
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(SearchErrorCode.INVALID_PARAMETER));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, Integer.MIN_VALUE})
    @DisplayName("순서 경계가 양수가 아니면 INVALID_PARAMETER다")
    void ofSequence_failure_notPositive(int sequence) {
        // when & then
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> SearchMessageListParameters.of(
                        sequence,
                        null
                )
        )
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(SearchErrorCode.INVALID_PARAMETER));
    }
}

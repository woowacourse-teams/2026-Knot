package com.knot.backend.search.application.dto.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchConversationListParametersTest {

    @Test
    @DisplayName("페이지 크기를 생략하면 20개이며 커서는 없다")
    void ofDefaults_success() {
        // when
        SearchConversationListParameters parameters = SearchConversationListParameters.of(
                null,
                null
        );

        // then
        assertThat(parameters.size()).isEqualTo(20);
        assertThat(parameters.cursor()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    @DisplayName("허용된 크기와 전달된 커서는 그대로 유지한다")
    void ofAllowedSize_success(int size) {
        // when
        SearchConversationListParameters parameters = SearchConversationListParameters.of(
                "cursor",
                size
        );

        // then
        assertThat(parameters.size()).isEqualTo(size);
        assertThat(parameters.cursor()).isEqualTo("cursor");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101, Integer.MAX_VALUE})
    @DisplayName("허용 범위를 벗어난 크기는 INVALID_PARAMETER다")
    void ofInvalidSize_failure(int size) {
        // when & then
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> SearchConversationListParameters.of(
                        null,
                        size
                )
        )
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(SearchErrorCode.INVALID_PARAMETER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    @DisplayName("명시적으로 전달한 빈 커서는 거절한다")
    void ofBlankCursor_failure(String cursor) {
        // when & then
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> SearchConversationListParameters.of(
                        cursor,
                        null
                )
        )
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(SearchErrorCode.INVALID_PARAMETER));
    }
}

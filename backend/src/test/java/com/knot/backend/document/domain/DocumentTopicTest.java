package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentTopicTest {

    @Test
    @DisplayName("NFC와 공백 정규화 결과가 같은 주제는 같은 값이다")
    void of_success_normalizedEquality() {
        // when
        DocumentTopic topic = DocumentTopic.of("　가\u00a0  검색　");

        // then
        assertThat(topic.value()).isEqualTo("가 검색");
        assertThat(topic).isEqualTo(DocumentTopic.of("가 검색"));
        assertThat(topic.hashCode()).isEqualTo(
                DocumentTopic.of("가 검색")
                        .hashCode()
        );
        assertThat(topic).isNotEqualTo(DocumentTopic.of("가 검색 기능"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t", "\u00a0", "\u2007", "\u202f", "　"})
    @DisplayName("누락과 Unicode 공백뿐인 주제는 생성할 수 없다")
    void of_failure_blank(String value) {
        // when & then
        assertThatThrownBy(() -> DocumentTopic.of(value))
                .hasMessage(DocumentErrorCode.INVALID_DOCUMENT_DATA.getMessage());
    }
}

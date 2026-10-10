package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentGenerationInputTest {

    @Test
    @DisplayName("검증된 주제를 재사용하고 원문을 그대로 유지한다")
    void from_success_preservesInput() {
        // given
        DocumentTopic topic = DocumentTopic.of("　검색\u00a0 조건　");
        String transcript = "　원문\n발언　";

        // when
        DocumentGenerationInput input = DocumentGenerationInput.from(
                transcript,
                topic
        );

        // then
        assertThat(input.getTopic()).isSameAs(topic);
        assertThat(input.getTranscriptContent()).isEqualTo(transcript);
    }

    @Test
    @DisplayName("검증된 주제를 전달하더라도 원문이 비었으면 생성 입력을 만들지 않는다")
    void from_failure_blankTranscript() {
        // given
        DocumentTopic topic = DocumentTopic.of("검색");

        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationInput.from(
                        "\u00a0",
                        topic
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT.getMessage());
    }

    @Test
    @DisplayName("주제 객체가 누락되면 생성 입력을 만들지 않는다")
    void from_failure_missingTopic() {
        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationInput.from(
                        "원문",
                        null
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT.getMessage());
    }
}

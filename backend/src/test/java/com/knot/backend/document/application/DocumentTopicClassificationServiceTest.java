package com.knot.backend.document.application;

import com.knot.backend.document.domain.DocumentTopic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.application.dto.result.DocumentTopicClassificationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.exception.LlmException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentTopicClassificationServiceTest {

    @Mock
    private DocumentTopicClassifier classifier;
    private DocumentTopicClassificationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentTopicClassificationService(classifier);
    }

    @Test
    @DisplayName("상위 서비스에 주제 목록을 반환하고 짧은 유효한 논의를 제외하지 않는다")
    void classify_success() {
        // given
        when(classifier.classify("검색하자")).thenReturn(List.of(DocumentTopic.of("검색")));

        // when
        DocumentTopicClassificationResult result = service.classify("검색하자");

        // then
        assertThat(result.topicNames()).containsExactly("검색");
        assertThat(result.isNoContent()).isFalse();
    }

    @Test
    @DisplayName("정상 빈 목록은 내용 없음으로 판정한다")
    void classify_success_noContent() {
        // given
        when(classifier.classify("안녕하세요")).thenReturn(List.of());

        // when
        DocumentTopicClassificationResult result = service.classify("안녕하세요");

        // then
        assertThat(result.isNoContent()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t", "　\u00a0"})
    @DisplayName("저장 원문이 없거나 Unicode 공백뿐이면 호출 전에 실패한다")
    void classify_failure_invalidInput(String content) {
        // when & then
        assertThatThrownBy(() -> service.classify(content))
                .hasMessage(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_INPUT.getMessage());
        verifyNoInteractions(classifier);
    }

    @Test
    @DisplayName("분류 실패는 상위 서비스에 그대로 전달한다")
    void classify_failure_providerError() {
        // given
        LlmException failure = new LlmException(LlmErrorCode.LLM_TIMEOUT);
        when(classifier.classify("논의")).thenThrow(failure);

        // when & then
        assertThatThrownBy(() -> service.classify("논의")).isSameAs(failure);
    }

    @Test
    @DisplayName("결과 생성 후 입력 목록이 바뀌어도 분류 결과는 유지한다")
    void result_success_defensiveCopy() {
        // given
        List<DocumentTopic> topics = new ArrayList<>(List.of(DocumentTopic.of("검색")));

        // when
        DocumentTopicClassificationResult result = new DocumentTopicClassificationResult(topics);
        topics.clear();

        // then
        assertThat(result.topicNames()).containsExactly("검색");
    }
}

package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.exception.LlmException;
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
class DocumentGenerationServiceTest {

    @Mock
    private DocumentGenerator generator;
    private DocumentGenerationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentGenerationService(generator);
    }

    @Test
    @DisplayName("상위 실행기에 단일 주제의 생성 결과를 전달하고 전체 원문을 그대로 넘긴다")
    void generate_success() {
        // given
        String transcript = "검색하자\n알림은 나중에 논의하자";
        DocumentGenerationResult expected = new DocumentGenerationResult(
                "검색",
                null,
                "## 핵심 요약\n검색을 논의했다."
        );
        when(
                generator.generate(
                        transcript,
                        "검색"
                )
        ).thenReturn(expected);

        // when
        DocumentGenerationResult result = service.generate(
                transcript,
                "검색"
        );

        // then
        assertThat(result).isSameAs(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t", "　\u00a0"})
    @DisplayName("원문·주제가 없거나 공백뿐이면 호출 전에 실패한다")
    void generate_failure_invalidInput(String input) {
        assertThatThrownBy(
                () -> service.generate(
                        input,
                        "검색"
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT.getMessage());
        assertThatThrownBy(
                () -> service.generate(
                        "검색하자",
                        input
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT.getMessage());
        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("외부 호출 실패를 빈 성공 결과로 바꾸지 않는다")
    void generate_failure_providerError() {
        // given
        LlmException failure = new LlmException(LlmErrorCode.LLM_TIMEOUT);
        when(
                generator.generate(
                        "검색하자",
                        "검색"
                )
        ).thenThrow(failure);

        // when & then
        assertThatThrownBy(
                () -> service.generate(
                        "검색하자",
                        "검색"
                )
        ).isSameAs(failure);
    }
}

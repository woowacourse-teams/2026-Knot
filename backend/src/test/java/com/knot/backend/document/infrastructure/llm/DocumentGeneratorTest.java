package com.knot.backend.document.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.exception.LlmException;
import com.knot.backend.global.infrastructure.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DocumentGeneratorTest {

    @Mock
    private LlmClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private DocumentGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DocumentGenerator(
                new DocumentGenerationPrompt(mapper),
                client,
                mapper,
                new DocumentMarkdownValidator()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"요약", "", "　\u00a0"})
    @NullSource
    @DisplayName("제목 바깥 공백만 정리하고 Markdown 내부 형식을 보존하며 빈 요약은 null로 만든다")
    void generate_success_nullableSummary(String summary) {
        // given
        String content = "## 핵심 요약\n검색을 논의했다.\n\n## 배경\n문서가  많아졌다.\n";
        when(client.complete(any())).thenReturn(
                mapper.writeValueAsString(
                        mapper.createObjectNode()
                                .put(
                                        "title",
                                        "　 검색 논의 \u00a0"
                                )
                                .put(
                                        "summary",
                                        summary
                                )
                                .put(
                                        "content",
                                        content
                                )
                )
        );

        // when
        DocumentGenerationResult result = generator.generate(
                "검색하자",
                "검색"
        );

        // then
        assertThat(result.title()).isEqualTo("검색 논의");
        assertThat(result.content()).isEqualTo(content);
        String expectedSummary = expectedSummary(summary);
        assertThat(result.summary()).isEqualTo(expectedSummary);
        assertThat(result.toString()).doesNotContain(
                "검색",
                "문서가"
        );
        verify(client).complete(any());
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "　", "not json", "[]", "null", "{}", "{\"title\":\"제목\",\"content\":\"## 핵심 요약\\n내용\"}",
            "{\"title\":1,\"summary\":null,\"content\":\"본문\"}", "{\"title\":\"제목\",\"summary\":[],\"content\":\"본문\"}",
            "{\"title\":\"제목\",\"summary\":false,\"content\":\"본문\"}",
            "{\"title\":\"제목\",\"summary\":null,\"content\":{}}",
            "{\"title\":null,\"summary\":null,\"content\":\"본문\"}",
            "{\"title\":\"　\",\"summary\":null,\"content\":\"본문\"}",
            "{\"title\":\"제목\",\"summary\":null,\"content\":\"　\"}",
            "{\"title\":\"제목\",\"summary\":null,\"content\":null}",
            "{\"title\":\"제목\",\"summary\":null,\"content\":\"## 핵심 요약\\n내용\",\"topic\":\"변경\"}",
            "{\"title\":\"제목\",\"title\":\"중복\",\"summary\":null,\"content\":\"## 핵심 요약\\n내용\"}",
            "{\"title\":\"제목\",\"summary\":null,\"content\":\"## 핵심 요약\\n내용\"} {}",
            "```json\n{\"title\":\"제목\",\"summary\":null,\"content\":\"본문\"}\n```"})
    @DisplayName("응답의 JSON 구조·실제 타입·필수값이 잘못되면 결과를 반환하지 않는다")
    void generate_failure_invalidJsonContract(String response) {
        // given
        when(client.complete(any())).thenReturn(response);

        // when & then
        assertThatThrownBy(
                () -> generator.generate(
                        "검색하자",
                        "검색"
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE.getMessage());
        verify(client).complete(any());
        verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("정상 JSON이어도 잘못된 Markdown은 거절한다")
    void generate_failure_invalidMarkdown() {
        // given
        when(client.complete(any()))
                .thenReturn("{\"title\":\"제목\",\"summary\":null,\"content\":\"## 핵심 요약\\n요약\\n## 할 일\\n- 없음\"}");

        // when & then
        assertThatThrownBy(
                () -> generator.generate(
                        "검색하자",
                        "검색"
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE.getMessage());
    }

    private String expectedSummary(String summary) {
        if ("요약".equals(summary)) {
            return summary;
        }
        return null;
    }

    @ParameterizedTest
    @EnumSource(value = LlmErrorCode.class, names = {"LLM_RATE_LIMITED", "LLM_TIMEOUT", "LLM_OUTPUT_LIMIT_EXCEEDED",
            "LLM_INPUT_LIMIT_EXCEEDED"})
    @DisplayName("공급자 오류는 원래 예외로 전달하며 숨은 재시도를 하지 않는다")
    void generate_failure_providerError(LlmErrorCode code) {
        // given
        LlmException failure = new LlmException(code);
        when(client.complete(any())).thenThrow(failure);

        // when & then
        assertThatThrownBy(
                () -> generator.generate(
                        "검색하자",
                        "검색"
                )
        ).isSameAs(failure);
        verify(client).complete(any());
        verifyNoMoreInteractions(client);
    }
}

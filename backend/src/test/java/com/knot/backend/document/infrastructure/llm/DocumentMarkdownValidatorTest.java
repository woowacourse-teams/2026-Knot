package com.knot.backend.document.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentMarkdownValidatorTest {

    private final DocumentMarkdownValidator validator = new DocumentMarkdownValidator();

    @ParameterizedTest
    @ValueSource(strings = {"## 핵심 요약\n검색을 논의했다.", "## 핵심 요약\n요약\n## 보류\n조건부 개발\n## 할 일\n수정안 작성\n## 배경\n근거",
            "## 핵심 요약\n요약\n## 결정\n합의\n## 보류\n조건\n## 미결정\n쟁점\n## 할 일\n작업\n## 우려\n비용",
            "## 핵심 요약\n요약\n## 배경\n```text\n## 결정\n코드 안의 문장\n```", "## 핵심 요약\n요약\n## 제안\n결정된 담당자는 없음",
            "## 핵심 요약\n요약\n## 배경\n~~~text\n## 결정\n문장\n~~~"})
    @DisplayName("없는 섹션은 생략하고 코드 안 heading과 자연어의 없음은 허용한다")
    void validate_success_sections(String content) {
        assertThatCode(
                () -> validator.validate(
                        new DocumentGenerationResult(
                                "제목",
                                null,
                                content
                        )
                )
        ).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"본문", "\n## 핵심 요약\n요약", "# 제목\n## 핵심 요약\n요약", "## 핵심 요약", "## 핵심 요약\n　",
            "## 핵심 요약\n요약\n## 핵심 요약\n중복", "## 핵심 요약\n요약\n## 결정\n합의\n## 결정\n중복", "## 핵심 요약\n요약\n## 할 일\n작업\n## 보류\n조건",
            "## 핵심 요약\n요약\n## 배경\n근거\n## 결정\n합의", "## 핵심 요약\n요약\n## 할 일\n", "## 핵심 요약\n요약\n## 할 일\n- (없음)",
            "## 핵심 요약\n요약\n## 결정\n해당 없음", "## 핵심 요약\n요약\n## 보류\n- 없음", "## 핵심 요약\n요약\n# 다른 주제\n본문",
            "## 핵심 요약\n요약\n```text\n미완료"})
    @DisplayName("핵심 요약과 섹션 순서·중복·본문·placeholder 계약을 위반하면 거절한다")
    void validate_failure_sections(String content) {
        assertThatThrownBy(
                () -> validator.validate(
                        new DocumentGenerationResult(
                                "제목",
                                null,
                                content
                        )
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[자료](relative.md)", "![이미지](image.png)", "https://example.com", "<a href='x'>자료</a>",
            "[자료][source]\n[source]: relative.md", "<img src='x'>", "www.example.com"})
    @DisplayName("생성 본문에 문서·외부·이미지 링크를 넣지 않는다")
    void validate_failure_links(String link) {
        assertThatThrownBy(
                () -> validator.validate(
                        new DocumentGenerationResult(
                                "제목",
                                null,
                                "## 핵심 요약\n" + link
                        )
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "[자료](guide.md)"})
    @DisplayName("제목과 요약에서도 링크를 거절한다")
    void validate_failure_metadataLinks(String link) {
        assertThatThrownBy(
                () -> validator.validate(
                        new DocumentGenerationResult(
                                link,
                                null,
                                "## 핵심 요약\n내용"
                        )
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE.getMessage());
        assertThatThrownBy(
                () -> validator.validate(
                        new DocumentGenerationResult(
                                "제목",
                                link,
                                "## 핵심 요약\n내용"
                        )
                )
        ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE.getMessage());
    }
}

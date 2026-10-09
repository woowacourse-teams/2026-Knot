package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.document.application.DocumentTopicClassificationService;
import com.knot.backend.document.application.DocumentGenerationService;
import com.knot.backend.document.infrastructure.llm.DocumentGenerationPrompt;
import com.knot.backend.document.application.DocumentGenerator;
import com.knot.backend.document.infrastructure.llm.LlmDocumentGenerator;
import com.knot.backend.document.infrastructure.llm.DocumentMarkdownValidator;
import com.knot.backend.document.application.DocumentTopicClassifier;
import com.knot.backend.document.infrastructure.llm.LlmDocumentTopicClassifier;
import com.knot.backend.document.infrastructure.llm.DocumentTopicPrompt;
import com.knot.backend.global.infrastructure.llm.LmStudioClient;
import com.knot.backend.global.infrastructure.llm.LlmClient;
import java.net.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.exception.LlmException;
import tools.jackson.databind.ObjectMapper;

class LlmConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withBean(
            ObjectMapper.class,
            ObjectMapper::new
    )
            .withUserConfiguration(
                    LlmConfig.class,
                    DocumentGenerationService.class,
                    DocumentGenerationPrompt.class,
                    LlmDocumentGenerator.class,
                    DocumentMarkdownValidator.class,
                    DocumentTopicPrompt.class,
                    LlmDocumentTopicClassifier.class,
                    DocumentTopicClassificationService.class
            );

    @Test
    @DisplayName("비활성 기본값에서는 LLM 관련 Bean과 허위 내용 없음 구현을 만들지 않는다")
    void configuration_success_disabled() {
        // when & then
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LmStudioClient.class);
            assertThat(context).doesNotHaveBean(DocumentTopicClassificationService.class);
            assertThat(context).doesNotHaveBean(DocumentGenerationService.class);
            assertThat(context).doesNotHaveBean(DocumentGenerator.class);
            assertThat(context).doesNotHaveBean(DocumentGenerationPrompt.class);
            assertThat(context).doesNotHaveBean(DocumentMarkdownValidator.class);
        });
    }

    @Test
    @DisplayName("활성 연결 설정은 외부 호출 없이 분류 유즈케이스를 조립한다")
    void configuration_success_enabled() {
        // when & then
        runner.withPropertyValues(
                "knot.llm.enabled=true",
                "knot.llm.base-url=https://llm.example.com",
                "knot.llm.api-token=test-secret",
                "knot.llm.model=test-model"
        )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DocumentTopicClassificationService.class);
                    assertThat(context.getBean(DocumentTopicClassifier.class))
                            .isInstanceOf(LlmDocumentTopicClassifier.class);
                    assertThat(context).hasSingleBean(DocumentGenerationService.class);
                    assertThat(context).hasSingleBean(DocumentGenerator.class);
                    DocumentGenerator generator = context.getBean(DocumentGenerator.class);
                    assertThat(generator).isInstanceOf(LlmDocumentGenerator.class);
                    assertThat(context).hasSingleBean(LmStudioClient.class);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(LmStudioClient.class);
                    assertThat(
                            context.getBean(HttpClient.class)
                                    .followRedirects()
                    ).isEqualTo(HttpClient.Redirect.NEVER);
                });
    }

    @Test
    @DisplayName("활성 설정에서 필수 인증값이 빠지면 조립에 실패한다")
    void configuration_failure_missingCredentials() {
        // when & then
        runner.withPropertyValues(
                "knot.llm.enabled=true",
                "knot.llm.base-url=https://llm.example.com"
        )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("지원하지 않는 공급자를 설정하면 조용히 다른 전략을 선택하지 않는다")
    void configuration_failure_unsupportedProvider() {
        // when & then
        runner.withPropertyValues(
                "knot.llm.enabled=true",
                "knot.llm.base-url=https://llm.example.com",
                "knot.llm.api-token=test-secret",
                "knot.llm.model=test-model",
                "knot.llm.provider=unsupported"
        )
                .run(context -> assertThat(context).hasFailed());
    }
    @ParameterizedTest
    @ValueSource(strings = {"generation-system.txt", "generation-template.md", "generation-schema.json"})
    @DisplayName("작성 리소스가 빠지면 시작 시 명시적인 LLM 설정 오류로 실패한다")
    void configuration_failure_missingWriterResource(String filename) {
        runner.withClassLoader(new FilteredClassLoader(new ClassPathResource("llm/document/" + filename)))
                .withPropertyValues(
                        "knot.llm.enabled=true",
                        "knot.llm.base-url=https://llm.example.com",
                        "knot.llm.api-token=test-secret",
                        "knot.llm.model=test-model"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(LlmException.class)
                            .hasRootCauseMessage(LlmErrorCode.LLM_INVALID_CONFIGURATION.getMessage());
                });
    }
}

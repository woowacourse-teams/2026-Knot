package com.knot.backend.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.search.application.DocumentEmbeddingClient;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.infrastructure.gemini.GeminiEmbeddingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class EmbeddingClientConfigTest {
    private static final String[] OPENAI_COMPATIBLE_CONNECTION = {"llm.base-uri=http://localhost:1234/v1",
            "llm.api-key=test-api-key", "llm.model=test-model", "llm.max-tokens=512", "llm.temperature=0.2",
            "llm.request-timeout=PT5S"};
    private static final String[] GEMINI_CONNECTION = {"llm.gemini.base-uri=https://generativelanguage.googleapis.com",
            "llm.gemini.api-key=gemini-secret", "llm.gemini.embedding-model=gemini-embedding-001",
            "llm.gemini.request-timeout=PT5S", "llm.embedding.dimensions=1024"};

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withBean(
            ObjectMapper.class,
            ObjectMapper::new
    )
            .withUserConfiguration(
                    SearchConfig.class,
                    EmbeddingClientConfig.class
            );

    @Test
    @DisplayName("임베딩 provider 설정이 없으면 fake 임베딩 클라이언트를 등록한다")
    void embeddingProvider_missing_registersFakeClient() {
        // given & when & then
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DocumentEmbeddingClient.class);
            assertThat(context.getBean(DocumentEmbeddingClient.class)).isInstanceOf(FakeDocumentEmbeddingClient.class);
            assertThat(context).doesNotHaveBean("embeddingLlmHttpClient");
        });
    }

    @Test
    @DisplayName("llm.embedding.provider=openai-compatible이면 OpenAI 호환 임베딩 클라이언트와 임베딩 전용 HttpClient를 등록한다")
    void embeddingProvider_openAiCompatible_registersOpenAiCompatibleClient() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(OPENAI_COMPATIBLE_CONNECTION)
                .withPropertyValues("llm.embedding.provider=openai-compatible");

        // when & then
        runner.run(context -> {
            assertThat(context).hasSingleBean(DocumentEmbeddingClient.class);
            assertThat(context.getBean(DocumentEmbeddingClient.class))
                    .isInstanceOf(OpenAiCompatibleEmbeddingClient.class);
            assertThat(context).hasBean("embeddingLlmHttpClient");
        });
    }

    @Test
    @DisplayName("채팅 provider가 openai-compatible이어도 임베딩 provider가 fake면 fake 임베딩 클라이언트를 유지한다")
    void embeddingProvider_chatProviderDiffers_keepsFakeClient() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(OPENAI_COMPATIBLE_CONNECTION)
                .withPropertyValues(
                        "llm.chat.provider=openai-compatible",
                        "llm.embedding.provider=fake"
                );

        // when & then
        runner.run(context -> {
            assertThat(context.getBean(DocumentEmbeddingClient.class)).isInstanceOf(FakeDocumentEmbeddingClient.class);
            assertThat(context).doesNotHaveBean("embeddingLlmHttpClient");
        });
    }

    @Test
    @DisplayName("llm.embedding.provider=gemini이면 Gemini 임베딩 클라이언트와 임베딩 전용 HttpClient를 등록한다")
    void embeddingProvider_gemini_registersGeminiClient() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(GEMINI_CONNECTION)
                .withPropertyValues("llm.embedding.provider=gemini");

        // when & then
        runner.run(context -> {
            assertThat(context).hasSingleBean(DocumentEmbeddingClient.class);
            assertThat(context.getBean(DocumentEmbeddingClient.class)).isInstanceOf(GeminiEmbeddingClient.class);
            assertThat(context).hasBean("embeddingLlmHttpClient");
        });
    }

    @Test
    @DisplayName("llm.embedding.provider=gemini인데 API 키가 비어 있으면 검색 설정 오류로 기동에 실패한다")
    void embeddingProvider_gemini_blankKeyFailsStartup() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(
                "llm.embedding.provider=gemini",
                "llm.gemini.base-uri=https://generativelanguage.googleapis.com",
                "llm.gemini.api-key=",
                "llm.gemini.embedding-model=gemini-embedding-001",
                "llm.gemini.request-timeout=PT5S",
                "llm.embedding.dimensions=1024"
        );

        // when & then
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOfSatisfying(
                            SearchException.class,
                            exception -> assertThat(exception.searchErrorCode())
                                    .isEqualTo(SearchErrorCode.SEARCH_CONFIGURATION_INVALID)
                    );
        });
    }

    @Test
    @DisplayName("임베딩 provider가 gemini면 OpenAI 호환 접속 설정이 없어도 기동한다")
    void embeddingProvider_gemini_doesNotRequireOpenAiCompatibleConnection() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(GEMINI_CONNECTION)
                .withPropertyValues(
                        "llm.chat.provider=fake",
                        "llm.embedding.provider=gemini"
                );

        // when & then
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DocumentEmbeddingClient.class)).isInstanceOf(GeminiEmbeddingClient.class);
        });
    }

    @Test
    @DisplayName("llm.provider만 설정하면 임베딩 provider가 그 값을 그대로 따른다")
    void embeddingProvider_legacyProviderOnly_followsLegacyValue() {
        // given
        ApplicationContextRunner runner = contextRunner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(OPENAI_COMPATIBLE_CONNECTION)
                .withPropertyValues("llm.provider=openai-compatible");

        // when & then
        runner.run(context -> {
            assertThat(context.getBean(DocumentEmbeddingClient.class))
                    .isInstanceOf(OpenAiCompatibleEmbeddingClient.class);
            assertThat(context).hasBean("embeddingLlmHttpClient");
        });
    }
}

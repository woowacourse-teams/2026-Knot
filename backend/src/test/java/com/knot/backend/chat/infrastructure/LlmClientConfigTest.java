package com.knot.backend.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.chat.application.LlmClient;
import com.knot.backend.chat.infrastructure.anthropic.AnthropicLlmClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class LlmClientConfigTest {
    private static final String[] OPENAI_COMPATIBLE_CONNECTION = {"llm.base-uri=http://localhost:1234/v1",
            "llm.api-key=test-api-key", "llm.model=test-model", "llm.max-tokens=512", "llm.temperature=0.2",
            "llm.request-timeout=PT5S"};
    private static final String[] ANTHROPIC_CONNECTION = {"llm.anthropic.base-uri=https://api.anthropic.com",
            "llm.anthropic.api-key=test-anthropic-key", "llm.anthropic.model=claude-opus-5",
            "llm.anthropic.effort=medium", "llm.anthropic.max-tokens=4096", "llm.anthropic.request-timeout=PT5S"};

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withBean(
            ObjectMapper.class,
            ObjectMapper::new
    )
            .withUserConfiguration(
                    FakeLlmClient.class,
                    LlmClientConfig.class
            );

    @Test
    @DisplayName("채팅 provider 설정이 없으면 fake 채팅 클라이언트를 등록한다")
    void chatProvider_missing_registersFakeClient() {
        // given & when & then
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(FakeLlmClient.class);
            assertThat(context).doesNotHaveBean("chatLlmHttpClient");
        });
    }

    @Test
    @DisplayName("llm.chat.provider=openai-compatible이면 OpenAI 호환 채팅 클라이언트와 채팅 전용 HttpClient를 등록한다")
    void chatProvider_openAiCompatible_registersOpenAiCompatibleClient() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(OPENAI_COMPATIBLE_CONNECTION)
                .withPropertyValues("llm.chat.provider=openai-compatible");

        // when & then
        runner.run(context -> {
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class);
            assertThat(context).hasBean("chatLlmHttpClient");
        });
    }

    @Test
    @DisplayName("llm.chat.provider=anthropic이면 Anthropic 채팅 클라이언트와 채팅 전용 HttpClient를 등록한다")
    void chatProvider_anthropic_registersAnthropicClient() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(ANTHROPIC_CONNECTION)
                .withPropertyValues("llm.chat.provider=anthropic");

        // when & then
        runner.run(context -> {
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(AnthropicLlmClient.class);
            assertThat(context).hasBean("chatLlmHttpClient");
            assertThat(context).doesNotHaveBean(OpenAiCompatibleLlmClient.class);
        });
    }

    @Test
    @DisplayName("anthropic provider인데 API 키가 비어 있으면 컨텍스트 기동이 실패한다")
    void chatProvider_anthropic_blankApiKey_failsToStart() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(ANTHROPIC_CONNECTION)
                .withPropertyValues(
                        "llm.chat.provider=anthropic",
                        "llm.anthropic.api-key="
                );

        // when & then
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("anthropic 접속 설정은 openai-compatible 채팅 클라이언트 선택에 영향을 주지 않는다")
    void chatProvider_openAiCompatible_ignoresAnthropicSettings() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(OPENAI_COMPATIBLE_CONNECTION)
                .withPropertyValues(
                        "llm.chat.provider=openai-compatible",
                        "llm.anthropic.api-key="
                );

        // when & then
        runner.run(context -> {
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class);
            assertThat(context).hasBean("chatLlmHttpClient");
        });
    }

    @Test
    @DisplayName("임베딩 provider는 채팅 클라이언트 선택을 바꾸지 않는다")
    void chatProvider_embeddingProviderDiffers_keepsFakeChatClient() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(OPENAI_COMPATIBLE_CONNECTION)
                .withPropertyValues(
                        "llm.chat.provider=fake",
                        "llm.embedding.provider=openai-compatible"
                );

        // when & then
        runner.run(context -> {
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(FakeLlmClient.class);
            assertThat(context).doesNotHaveBean("chatLlmHttpClient");
        });
    }

    @Test
    @DisplayName("llm.provider만 설정하면 채팅 provider가 그 값을 그대로 따른다")
    void chatProvider_legacyProviderOnly_followsLegacyValue() {
        // given
        ApplicationContextRunner runner = contextRunner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(OPENAI_COMPATIBLE_CONNECTION)
                .withPropertyValues("llm.provider=openai-compatible");

        // when & then
        runner.run(context -> {
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class);
            assertThat(context).hasBean("chatLlmHttpClient");
        });
    }
}

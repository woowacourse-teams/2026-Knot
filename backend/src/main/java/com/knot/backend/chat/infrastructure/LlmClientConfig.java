package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.application.LlmClient;
import com.knot.backend.chat.infrastructure.anthropic.AnthropicLlmClient;
import com.knot.backend.chat.infrastructure.anthropic.AnthropicLlmProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({LlmProperties.class, AnthropicLlmProperties.class})
public class LlmClientConfig {

    @Bean(name = "chatLlmHttpClient")
    @ConditionalOnProperty(prefix = "llm.chat", name = "provider", havingValue = "openai-compatible")
    public HttpClient openAiCompatibleChatLlmHttpClient(LlmProperties properties) {
        properties.validate();
        return httpClient(properties.requestTimeout());
    }

    @Bean
    @ConditionalOnProperty(prefix = "llm.chat", name = "provider", havingValue = "openai-compatible")
    public LlmClient openAiCompatibleLlmClient(
            @Qualifier("chatLlmHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper,
            LlmProperties properties
    ) {
        return new OpenAiCompatibleLlmClient(
                httpClient,
                objectMapper,
                properties
        );
    }

    @Bean(name = "chatLlmHttpClient")
    @ConditionalOnProperty(prefix = "llm.chat", name = "provider", havingValue = "anthropic")
    public HttpClient anthropicChatLlmHttpClient(AnthropicLlmProperties properties) {
        properties.validate();
        return httpClient(properties.requestTimeout());
    }

    @Bean
    @ConditionalOnProperty(prefix = "llm.chat", name = "provider", havingValue = "anthropic")
    public LlmClient anthropicLlmClient(
            @Qualifier("chatLlmHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper,
            AnthropicLlmProperties properties
    ) {
        return new AnthropicLlmClient(
                httpClient,
                objectMapper,
                properties
        );
    }

    private static HttpClient httpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}

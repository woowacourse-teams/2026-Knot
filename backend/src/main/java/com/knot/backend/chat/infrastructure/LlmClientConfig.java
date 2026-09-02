package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.application.LlmClient;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "openai-compatible")
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientConfig {

    @Bean(name = "llmHttpClient")
    public HttpClient llmHttpClient(LlmProperties properties) {
        properties.validate();
        return HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    public LlmClient openAiCompatibleLlmClient(
            @Qualifier("llmHttpClient") HttpClient httpClient,
            tools.jackson.databind.ObjectMapper objectMapper,
            LlmProperties properties
    ) {
        return new OpenAiCompatibleLlmClient(
                httpClient,
                objectMapper,
                properties
        );
    }
}

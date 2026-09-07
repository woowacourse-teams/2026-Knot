package com.knot.backend.search.infrastructure;

import com.knot.backend.chat.infrastructure.LlmProperties;
import com.knot.backend.search.application.DocumentEmbeddingClient;
import com.knot.backend.search.application.EmbeddingProperties;
import com.knot.backend.search.application.SearchProperties;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({EmbeddingProperties.class, LlmProperties.class})
public class EmbeddingClientConfig {

    @Bean(name = "embeddingLlmHttpClient")
    @ConditionalOnProperty(prefix = "llm.embedding", name = "provider", havingValue = "openai-compatible")
    public HttpClient embeddingLlmHttpClient(LlmProperties properties) {
        properties.validate();
        return HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "llm.embedding", name = "provider", havingValue = "openai-compatible")
    public DocumentEmbeddingClient openAiCompatibleEmbeddingClient(
            @Qualifier("embeddingLlmHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper,
            LlmProperties llmProperties,
            EmbeddingProperties embeddingProperties
    ) {
        return new OpenAiCompatibleEmbeddingClient(
                httpClient,
                objectMapper,
                llmProperties,
                embeddingProperties
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "llm.embedding", name = "provider", havingValue = "fake", matchIfMissing = true)
    public DocumentEmbeddingClient fakeDocumentEmbeddingClient(SearchProperties properties) {
        return new FakeDocumentEmbeddingClient(properties);
    }
}

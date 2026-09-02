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
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "openai-compatible")
    public DocumentEmbeddingClient openAiCompatibleEmbeddingClient(
            @Qualifier("llmHttpClient") HttpClient httpClient,
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
    @ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "fake", matchIfMissing = true)
    public DocumentEmbeddingClient fakeDocumentEmbeddingClient(SearchProperties properties) {
        return new FakeDocumentEmbeddingClient(properties);
    }
}

package com.knot.backend.search.infrastructure;

import com.knot.backend.chat.infrastructure.LlmProperties;
import com.knot.backend.search.application.DocumentEmbeddingClient;
import com.knot.backend.search.application.EmbeddingProperties;
import com.knot.backend.search.application.SearchProperties;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.infrastructure.gemini.GeminiEmbeddingClient;
import com.knot.backend.search.infrastructure.gemini.GeminiEmbeddingProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({EmbeddingProperties.class, LlmProperties.class, GeminiEmbeddingProperties.class})
public class EmbeddingClientConfig {

    @Bean(name = "embeddingLlmHttpClient")
    @ConditionalOnProperty(prefix = "llm.embedding", name = "provider", havingValue = "openai-compatible")
    public HttpClient openAiCompatibleEmbeddingLlmHttpClient(LlmProperties properties) {
        properties.validate();
        return httpClient(properties.requestTimeout());
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

    // 키가 비면 여기서 기동이 실패한다(Anthropic 어댑터와 같은 fail-fast, 로드맵 Q38).
    @Bean(name = "embeddingLlmHttpClient")
    @ConditionalOnProperty(prefix = "llm.embedding", name = "provider", havingValue = "gemini")
    public HttpClient geminiEmbeddingLlmHttpClient(
            GeminiEmbeddingProperties properties,
            EmbeddingProperties embeddingProperties
    ) {
        properties.validate();
        if (embeddingProperties.dimensions() <= 0) {
            throw new SearchException(SearchErrorCode.SEARCH_CONFIGURATION_INVALID);
        }
        return httpClient(properties.requestTimeout());
    }

    @Bean
    @ConditionalOnProperty(prefix = "llm.embedding", name = "provider", havingValue = "gemini")
    public DocumentEmbeddingClient geminiEmbeddingClient(
            @Qualifier("embeddingLlmHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper,
            GeminiEmbeddingProperties properties,
            EmbeddingProperties embeddingProperties
    ) {
        return new GeminiEmbeddingClient(
                httpClient,
                objectMapper,
                properties,
                embeddingProperties
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "llm.embedding", name = "provider", havingValue = "fake", matchIfMissing = true)
    public DocumentEmbeddingClient fakeDocumentEmbeddingClient(SearchProperties properties) {
        return new FakeDocumentEmbeddingClient(properties);
    }

    private static HttpClient httpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}

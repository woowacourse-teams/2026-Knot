package com.knot.backend.search.infrastructure.gemini;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "llm.gemini")
public record GeminiEmbeddingProperties(
        URI baseUri,
        String apiKey,
        String embeddingModel,
        Duration requestTimeout,
        @DefaultValue("6") int retryMaxAttempts,
        @DefaultValue("PT5S") Duration retryInitialDelay
) {
    private static final String MODEL_RESOURCE_PREFIX = "models/";

    public void validate() {
        if (!isAbsoluteHttpUri(baseUri) || isBlank(apiKey) || isBlank(modelId()) || !isPositive(requestTimeout)
                || retryMaxAttempts < 1 || retryInitialDelay == null || retryInitialDelay.isNegative()) {
            throw new SearchException(SearchErrorCode.SEARCH_CONFIGURATION_INVALID);
        }
    }

    public URI batchEmbedContentsUri() {
        validate();
        String value = baseUri.toString();
        String separator = value.endsWith("/") ? "" : "/";
        return URI.create(value + separator + "v1beta/" + modelResourceName() + ":batchEmbedContents");
    }

    // 요청 본문의 model 필드는 "models/<모델>" 형식이어야 한다. 설정에 접두사가 있어도 없어도 같은 값을 만든다.
    public String modelResourceName() {
        return MODEL_RESOURCE_PREFIX + modelId();
    }

    private String modelId() {
        if (embeddingModel == null) {
            return null;
        }
        String trimmed = embeddingModel.trim();
        if (trimmed.startsWith(MODEL_RESOURCE_PREFIX)) {
            return trimmed.substring(MODEL_RESOURCE_PREFIX.length());
        }
        return trimmed;
    }

    private boolean isAbsoluteHttpUri(URI uri) {
        return uri != null && uri.isAbsolute() && uri.getHost() != null
                && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isPositive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}

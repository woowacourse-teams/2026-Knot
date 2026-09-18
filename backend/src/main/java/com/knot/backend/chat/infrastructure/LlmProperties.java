package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        URI baseUri,
        String apiKey,
        String model,
        int maxTokens,
        double temperature,
        Duration requestTimeout
) {

    public void validate() {
        if (!isAbsoluteHttpUri(baseUri) || isBlank(apiKey) || isBlank(model) || maxTokens <= 0 || temperature < 0
                || temperature > 2 || !isPositive(requestTimeout)) {
            throw new ChatException(ChatErrorCode.LLM_CONFIGURATION_INVALID);
        }
    }

    public URI chatCompletionsUri() {
        validate();
        String value = baseUri.toString();
        String separator = value.endsWith("/") ? "" : "/";
        return URI.create(value + separator + "chat/completions");
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

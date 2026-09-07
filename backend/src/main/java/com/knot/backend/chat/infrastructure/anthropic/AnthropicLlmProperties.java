package com.knot.backend.chat.infrastructure.anthropic;

import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.anthropic")
public record AnthropicLlmProperties(
        URI baseUri,
        String apiKey,
        String model,
        String effort,
        int maxTokens,
        Duration requestTimeout
) {
    private static final Set<String> EFFORT_LEVELS = Set.of(
            "low",
            "medium",
            "high",
            "xhigh",
            "max"
    );

    public void validate() {
        if (!isAbsoluteHttpUri(baseUri) || isBlank(apiKey) || isBlank(model) || !isEffortLevel(effort) || maxTokens <= 0
                || !isPositive(requestTimeout)) {
            throw new ChatException(ChatErrorCode.LLM_CONFIGURATION_INVALID);
        }
    }

    public URI messagesUri() {
        validate();
        String value = baseUri.toString();
        String separator = value.endsWith("/") ? "" : "/";
        return URI.create(value + separator + "v1/messages");
    }

    public String effortLevel() {
        return effort.toLowerCase(Locale.ROOT);
    }

    private boolean isEffortLevel(String value) {
        return value != null && EFFORT_LEVELS.contains(value.toLowerCase(Locale.ROOT));
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

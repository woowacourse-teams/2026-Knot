package com.knot.backend.global.config;

import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.exception.LlmException;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "knot.llm")
public class LlmProperties {
    private static final Set<String> LOOPBACK_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "[::1]"
    );

    private boolean enabled;
    private String provider = "lm-studio";
    private URI baseUrl;
    private String apiToken;
    private String model;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(120);
    private long maxResponseBytes = 262144;

    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        validateProvider();
        validateBaseUrl();
        validateCredentials();
        validateTimeouts();
        validateResponseLimit();
    }

    private void validateProvider() {
        if (!"lm-studio".equals(provider)) {
            throw invalidConfiguration();
        }
    }

    private void validateBaseUrl() {
        if (baseUrl == null || baseUrl.getHost() == null) {
            throw invalidConfiguration();
        }
        validateScheme();
        if (baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
            throw invalidConfiguration();
        }
        String path = baseUrl.getPath();
        if (path != null && !path.isEmpty() && !path.equals("/")) {
            throw invalidConfiguration();
        }
    }

    private void validateScheme() {
        if ("https".equals(baseUrl.getScheme())) {
            return;
        }
        if ("http".equals(baseUrl.getScheme()) && isLoopbackHost()) {
            return;
        }
        throw invalidConfiguration();
    }

    private boolean isLoopbackHost() {
        return LOOPBACK_HOSTS.contains(baseUrl.getHost());
    }

    private void validateCredentials() {
        if (apiToken == null || apiToken.isBlank() || apiToken.contains("\r") || apiToken.contains("\n")) {
            throw invalidConfiguration();
        }
        if (model == null || model.isBlank()) {
            throw invalidConfiguration();
        }
    }

    private void validateTimeouts() {
        validatePositiveDuration(connectTimeout);
        validatePositiveDuration(requestTimeout);
        if (connectTimeout.compareTo(requestTimeout) > 0) {
            throw invalidConfiguration();
        }
    }

    private void validatePositiveDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw invalidConfiguration();
        }
        try {
            if (duration.toMillis() < 1) {
                throw invalidConfiguration();
            }
        } catch (ArithmeticException exception) {
            throw invalidConfiguration();
        }
    }

    private void validateResponseLimit() {
        if (maxResponseBytes <= 0) {
            throw invalidConfiguration();
        }
    }

    private LlmException invalidConfiguration() {
        return new LlmException(LlmErrorCode.LLM_INVALID_CONFIGURATION);
    }
}

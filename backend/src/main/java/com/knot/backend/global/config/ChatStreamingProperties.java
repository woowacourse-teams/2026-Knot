package com.knot.backend.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "chat.streaming")
public class ChatStreamingProperties {
    private Duration timeout = Duration.ofSeconds(150);

    public long timeoutMillis() {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("chat.streaming.timeout must be positive");
        }
        long timeoutMillis = timeout.toMillis();
        if (timeoutMillis <= 0) {
            throw new IllegalStateException("chat.streaming.timeout must be at least one millisecond");
        }
        return timeoutMillis;
    }
}

package com.knot.backend.recording.infrastructure.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recording.audio-storage")
public record RecordingAudioStorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        String keyPrefix,
        Duration uploadUrlTtl
) {

    public boolean isConfigured() {
        return hasText(endpoint) && hasText(region) && hasText(bucket) && hasText(accessKey) == hasText(secretKey);
    }

    public boolean hasStaticCredentials() {
        return hasText(accessKey) && hasText(secretKey);
    }

    @Override
    public String toString() {
        return "RecordingAudioStorageProperties[endpoint=" + endpoint + ", bucket=" + bucket
                + ", accessKey=REDACTED, secretKey=REDACTED]";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

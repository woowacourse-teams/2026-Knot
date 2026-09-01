package com.knot.backend.workspace.infrastructure.notion.collector;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MicrometerNotionCollectionObserver implements NotionCollectionObserver {
    static final String SKIPPED_ELEMENT_METRIC = "notion.import.skipped.elements";
    private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerNotionCollectionObserver.class);
    private static final int MAX_TYPE_LENGTH = 64;

    private final MeterRegistry meterRegistry;

    MicrometerNotionCollectionObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(
                meterRegistry,
                "meterRegistry"
        );
    }

    @Override
    public void recordSkippedBlock(
            String blockType,
            int count
    ) {
        record(
                "block",
                blockType,
                count
        );
    }

    @Override
    public void recordSkippedProperty(
            String propertyType,
            int count
    ) {
        record(
                "property",
                propertyType,
                count
        );
    }

    private void record(
            String kind,
            String rawType,
            int count
    ) {
        if (count <= 0) {
            return;
        }
        String type = sanitizeType(rawType);
        meterRegistry.counter(
                SKIPPED_ELEMENT_METRIC,
                "kind",
                kind,
                "type",
                type
        )
                .increment(count);
        LOGGER.atWarn()
                .addKeyValue(
                        "kind",
                        kind
                )
                .addKeyValue(
                        "type",
                        type
                )
                .addKeyValue(
                        "count",
                        count
                )
                .log("Notion import element skipped");
    }

    private String sanitizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "unknown";
        }
        String normalized = rawType.toLowerCase(Locale.ROOT);
        StringBuilder sanitized = new StringBuilder(MAX_TYPE_LENGTH);
        for (int index = 0; index < normalized.length() && sanitized.length() < MAX_TYPE_LENGTH; index++) {
            char character = normalized.charAt(index);
            if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '_') {
                sanitized.append(character);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.isEmpty() ? "unknown" : sanitized.toString();
    }
}

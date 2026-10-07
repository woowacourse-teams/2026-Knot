package com.knot.backend.document.application.dto.result;

public record DocumentTopicResult(
        String topic,
        int documentCount
) {
}

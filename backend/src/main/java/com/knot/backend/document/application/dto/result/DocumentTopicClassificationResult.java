package com.knot.backend.document.application.dto.result;

import java.util.List;

public record DocumentTopicClassificationResult(List<String> topics) {

    public DocumentTopicClassificationResult {
        topics = List.copyOf(topics);
    }

    public boolean isNoContent() {
        return topics.isEmpty();
    }
}

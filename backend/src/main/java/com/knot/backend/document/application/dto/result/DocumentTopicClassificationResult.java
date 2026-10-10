package com.knot.backend.document.application.dto.result;

import com.knot.backend.document.domain.DocumentTopic;
import java.util.List;

public record DocumentTopicClassificationResult(List<DocumentTopic> topics) {

    public DocumentTopicClassificationResult {
        topics = List.copyOf(topics);
    }

    public static DocumentTopicClassificationResult fromNames(List<String> names) {
        return new DocumentTopicClassificationResult(
                names.stream()
                        .map(DocumentTopic::of)
                        .toList()
        );
    }

    public List<String> topicNames() {
        return topics.stream()
                .map(DocumentTopic::value)
                .toList();
    }

    public boolean isNoContent() {
        return topics.isEmpty();
    }
}

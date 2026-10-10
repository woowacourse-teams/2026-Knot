package com.knot.backend.document.application;

import com.knot.backend.document.domain.DocumentTopic;
import java.util.List;

public interface DocumentTopicClassifier {

    List<DocumentTopic> classify(String transcriptContent);
}

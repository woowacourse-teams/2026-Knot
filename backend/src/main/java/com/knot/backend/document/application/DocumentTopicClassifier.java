package com.knot.backend.document.application;

import java.util.List;

public interface DocumentTopicClassifier {

    List<String> classify(String transcriptContent);
}

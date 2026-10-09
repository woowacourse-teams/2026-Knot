package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentTopicClassificationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.infrastructure.llm.DocumentTopicClassifier;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knot.llm", name = "enabled", havingValue = "true")
public class DocumentTopicClassificationService {
    private static final Pattern BLANK_CONTENT = Pattern.compile("[\\p{javaWhitespace}\\p{Z}]*");

    private final DocumentTopicClassifier classifier;

    public DocumentTopicClassificationResult classify(String transcriptContent) {
        validateTranscriptContent(transcriptContent);
        List<String> topics = classifier.classify(transcriptContent);
        return new DocumentTopicClassificationResult(topics);
    }

    private void validateTranscriptContent(String transcriptContent) {
        if (transcriptContent == null || BLANK_CONTENT.matcher(transcriptContent)
                .matches()) {
            throw new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_INPUT);
        }
    }
}

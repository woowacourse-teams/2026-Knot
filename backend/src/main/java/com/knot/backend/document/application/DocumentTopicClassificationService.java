package com.knot.backend.document.application;

import com.knot.backend.document.domain.DocumentTopic;
import com.knot.backend.document.domain.DocumentText;
import com.knot.backend.document.application.dto.result.DocumentTopicClassificationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knot.llm", name = "enabled", havingValue = "true")
public class DocumentTopicClassificationService {

    private final DocumentTopicClassifier classifier;

    public DocumentTopicClassificationResult classify(String transcriptContent) {
        validateTranscriptContent(transcriptContent);
        List<DocumentTopic> topics = classifier.classify(transcriptContent);
        return new DocumentTopicClassificationResult(topics);
    }

    private void validateTranscriptContent(String transcriptContent) {
        if (DocumentText.isBlank(transcriptContent)) {
            throw new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_INPUT);
        }
    }
}

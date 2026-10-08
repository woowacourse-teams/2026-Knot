package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.infrastructure.llm.DocumentGenerator;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knot.llm", name = "enabled", havingValue = "true")
public class DocumentGenerationService {

    private static final Pattern BLANK = Pattern.compile("[\\p{javaWhitespace}\\p{Z}]*");

    private final DocumentGenerator generator;

    public DocumentGenerationResult generate(
            String transcriptContent,
            String topic
    ) {
        validateTranscriptContent(transcriptContent);
        validateTopic(topic);
        return generator.generate(
                transcriptContent,
                topic
        );
    }

    private void validateTranscriptContent(String transcriptContent) {
        if (transcriptContent == null || BLANK.matcher(transcriptContent)
                .matches()) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT);
        }
    }

    private void validateTopic(String topic) {
        if (topic == null || BLANK.matcher(topic)
                .matches()) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT);
        }
    }
}

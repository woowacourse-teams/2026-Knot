package com.knot.backend.document.application;

import com.knot.backend.document.domain.DocumentGenerationInput;
import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knot.llm", name = "enabled", havingValue = "true")
public class DocumentGenerationService {

    private final DocumentGenerator generator;

    public DocumentGenerationResult generate(
            String transcriptContent,
            String topic
    ) {
        DocumentGenerationInput input = DocumentGenerationInput.of(
                transcriptContent,
                topic
        );
        return generator.generate(input);
    }
}

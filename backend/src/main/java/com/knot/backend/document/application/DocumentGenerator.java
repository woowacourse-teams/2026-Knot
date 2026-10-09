package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentGenerationResult;

public interface DocumentGenerator {

    DocumentGenerationResult generate(
            String transcriptContent,
            String topic
    );
}

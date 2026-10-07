package com.knot.backend.document.application.dto.result;

public record DocumentGenerationInputResult(
        long transcriptId,
        String content
) {
}

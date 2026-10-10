package com.knot.backend.document.application.dto.result;

public record DocumentGenerationResult(
        String title,
        String summary,
        String content
) {

    @Override
    public String toString() {
        return "DocumentGenerationResult[redacted]";
    }
}

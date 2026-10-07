package com.knot.backend.document.application.dto.result;

public record DocumentGenerationInputResult(
        long transcriptId,
        long recordingSessionId,
        String content
) {
}

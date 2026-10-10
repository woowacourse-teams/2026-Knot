package com.knot.backend.search.application.dto.result;

public record SearchEvidenceItemResult(
        long documentId,
        String title,
        String topic,
        int rank
) {
}

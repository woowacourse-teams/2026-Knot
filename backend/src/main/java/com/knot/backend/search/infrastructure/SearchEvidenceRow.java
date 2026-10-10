package com.knot.backend.search.infrastructure;

public record SearchEvidenceRow(
        long messageId,
        long documentId,
        String title,
        String topic,
        short rank
) {
}

package com.knot.backend.document.application.dto.result;

public record DocumentConfirmationOverviewResult(
        long documentId,
        DocumentConfirmationSummaryResult summary,
        boolean confirmedByMe
) {
}

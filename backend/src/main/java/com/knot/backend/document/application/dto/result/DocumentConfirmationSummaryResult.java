package com.knot.backend.document.application.dto.result;

public record DocumentConfirmationSummaryResult(
        int confirmedCount,
        int pendingCount,
        int excludedCount
) {
}

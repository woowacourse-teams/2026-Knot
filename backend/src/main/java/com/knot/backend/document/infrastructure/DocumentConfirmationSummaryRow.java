package com.knot.backend.document.infrastructure;

record DocumentConfirmationSummaryRow(
        long documentId,
        long confirmedCount,
        long pendingCount,
        long excludedCount,
        boolean confirmedByMe
) {
}

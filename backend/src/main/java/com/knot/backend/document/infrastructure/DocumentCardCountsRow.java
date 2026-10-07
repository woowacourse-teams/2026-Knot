package com.knot.backend.document.infrastructure;

record DocumentCardCountsRow(
        long documentId,
        long confirmedCount,
        long pendingCount,
        long excludedCount
) {
}

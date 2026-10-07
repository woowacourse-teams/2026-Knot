package com.knot.backend.document.application.dto.result;

import com.knot.backend.document.domain.DocumentStatus;
import java.time.Instant;

public record DocumentConfirmationResult(
        long documentId,
        Instant confirmedAt,
        DocumentStatus documentStatus,
        Instant archivedAt,
        DocumentConfirmationSummaryResult confirmationSummary
) {
}

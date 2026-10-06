package com.knot.backend.document.application.dto.result;

import java.util.List;

public record DocumentConfirmationsResult(
        long documentId,
        DocumentConfirmationSummaryResult summary,
        boolean confirmedByMe,
        List<DocumentConfirmationItemResult> items,
        String nextCursor
) {
}

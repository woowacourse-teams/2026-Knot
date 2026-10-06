package com.knot.backend.document.application.dto.result;

import com.knot.backend.document.domain.DocumentStatus;
import java.time.Instant;

public record DocumentDetailSnapshot(
        long id,
        long recordingSessionId,
        String topic,
        String title,
        String summary,
        String content,
        DocumentStatus status,
        Instant createdAt,
        Instant archivedAt,
        long recordingDurationMillis,
        long sourceTranscriptId,
        boolean requiredByMe,
        Instant confirmedAtByMe,
        DocumentConfirmationSummaryResult confirmationSummary
) {
}

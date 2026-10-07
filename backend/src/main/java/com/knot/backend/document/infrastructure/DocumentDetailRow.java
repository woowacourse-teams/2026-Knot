package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentStatus;
import java.time.Instant;

record DocumentDetailRow(
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
        long confirmedCount,
        long pendingCount,
        long excludedCount
) {
}

package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentStatus;
import java.time.Instant;

record DocumentCardRow(
        long id,
        long recordingSessionId,
        String topic,
        String title,
        String summary,
        DocumentStatus status,
        Instant createdAt,
        long recordingDurationMillis,
        boolean requiredByMe,
        Instant confirmedAtByMe
) {
}

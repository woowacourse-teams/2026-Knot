package com.knot.backend.document.domain;

import java.time.Duration;
import java.time.Instant;

public class DocumentGenerationRetryPolicy {

    public boolean isAutomaticallyRetryable(DocumentGenerationFailureCause cause) {
        return switch (cause) {
            case TIMEOUT, RATE_LIMITED, UNAVAILABLE, EXECUTION_EXPIRED, STORAGE -> true;
            default -> false;
        };
    }

    public Instant calculateNextAttemptAt(
            Instant now,
            Duration backoff
    ) {
        return now.plus(backoff);
    }
}

package com.knot.backend.document.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentGenerationRecoveryService {

    private final DocumentGenerationFailureService failures;

    public void recoverExpired(
            long workspaceId,
            long jobId,
            int expectedAttemptCount
    ) {
        failures.expireAttempt(
                workspaceId,
                jobId,
                expectedAttemptCount
        );
    }
}

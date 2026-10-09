package com.knot.backend.global.config;

import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "knot.document-generation.worker")
public class DocumentGenerationWorkerProperties {

    private boolean enabled;
    private int concurrency = 2;
    private int candidateLimit = 20;
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration recoveryInterval = Duration.ofSeconds(5);
    private Duration executionLease = Duration.ofSeconds(150);
    private Duration automaticRetryBackoff = Duration.ofSeconds(5);

    public void validate(LlmProperties llm) {
        if (!llm.isEnabled()) {
            throw new DocumentException(DocumentErrorCode.INVALID_GENERATION_WORKER_CONFIGURATION);
        }
        validateCapacity();
        validateDuration(pollInterval);
        validateDuration(recoveryInterval);
        validateDuration(automaticRetryBackoff);
        validateDuration(executionLease);
        if (executionLease.compareTo(
                llm.getRequestTimeout()
                        .plusSeconds(10)
        ) < 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_GENERATION_WORKER_CONFIGURATION);
        }
    }

    private void validateCapacity() {
        validateConcurrency();
        validateCandidateLimit();
    }

    private void validateConcurrency() {
        if (concurrency < 1 || concurrency > 16) {
            throw new DocumentException(DocumentErrorCode.INVALID_GENERATION_WORKER_CONFIGURATION);
        }
    }

    private void validateCandidateLimit() {
        if (candidateLimit < concurrency || candidateLimit > 100) {
            throw new DocumentException(DocumentErrorCode.INVALID_GENERATION_WORKER_CONFIGURATION);
        }
    }

    private void validateDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new DocumentException(DocumentErrorCode.INVALID_GENERATION_WORKER_CONFIGURATION);
        }
        try {
            if (duration.toMillis() < 1) {
                throw new DocumentException(DocumentErrorCode.INVALID_GENERATION_WORKER_CONFIGURATION);
            }
        } catch (ArithmeticException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_GENERATION_WORKER_CONFIGURATION);
        }
    }
}

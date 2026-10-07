package com.knot.backend.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.Getter;

@Getter
@Entity
@Table(name = "document_generation_execution_requests")
public class DocumentGenerationExecutionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private long jobId;

    @Column(name = "attempt_count", nullable = false, updatable = false)
    private int attemptCount;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    protected DocumentGenerationExecutionRequest() {}

    private DocumentGenerationExecutionRequest(
            long jobId,
            int attemptCount,
            Instant acceptedAt
    ) {
        validateJobId(jobId);
        validateAttemptCount(attemptCount);
        validateAcceptedAt(acceptedAt);
        this.jobId = jobId;
        this.attemptCount = attemptCount;
        this.acceptedAt = acceptedAt;
    }

    public static DocumentGenerationExecutionRequest accept(
            long jobId,
            int attemptCount,
            Instant acceptedAt
    ) {
        return new DocumentGenerationExecutionRequest(
                jobId,
                attemptCount,
                acceptedAt
        );
    }

    private void validateJobId(long jobId) {
        if (jobId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    private void validateAttemptCount(int attemptCount) {
        if (attemptCount <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    private void validateAcceptedAt(Instant acceptedAt) {
        if (acceptedAt == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
        try {
            int year = acceptedAt.atOffset(ZoneOffset.UTC)
                    .getYear();
            if (year < 1 || year > 9999) {
                throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
            }
        } catch (DateTimeException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }
}

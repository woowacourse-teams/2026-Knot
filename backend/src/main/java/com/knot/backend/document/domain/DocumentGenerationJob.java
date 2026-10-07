package com.knot.backend.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneOffset;
import lombok.Getter;

@Getter
@Entity
@Table(name = "document_generation_jobs")
public class DocumentGenerationJob {
    private static final Duration FAILURE_RETENTION = Duration.ofDays(7);
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transcript_id", nullable = false, updatable = false)
    private long transcriptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentGenerationJobStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected DocumentGenerationJob() {}

    public void recordFailure(Instant failedAt) {
        validateFailureState();
        validateFailureTime(failedAt);
        Instant deadline = failedAt.plus(FAILURE_RETENTION);
        validateFailureYear(deadline);
        status = DocumentGenerationJobStatus.FAILED;
        updatedAt = failedAt;
        lastFailedAt = failedAt;
        expiresAt = deadline;
    }

    private void validateFailureState() {
        if (status == DocumentGenerationJobStatus.SUCCEEDED) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    private void validateFailureTime(Instant failedAt) {
        if (failedAt == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
        validateFailureYear(failedAt);
        if (failedAt.isBefore(updatedAt)) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    private void validateFailureYear(Instant time) {
        try {
            int year = time.atOffset(ZoneOffset.UTC)
                    .getYear();
            if (year < 1 || year > 9999) {
                throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
            }
        } catch (DateTimeException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    private DocumentGenerationJob(
            long transcriptId,
            Instant createdAt
    ) {
        if (transcriptId <= 0 || createdAt == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
        this.transcriptId = transcriptId;
        this.status = DocumentGenerationJobStatus.QUEUED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static DocumentGenerationJob queue(
            long transcriptId,
            Instant createdAt
    ) {
        return new DocumentGenerationJob(
                transcriptId,
                createdAt
        );
    }
}

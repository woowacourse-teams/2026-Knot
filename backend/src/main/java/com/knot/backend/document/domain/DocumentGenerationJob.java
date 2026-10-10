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
    private static final int MAX_USER_RETRIES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private long batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private DocumentGenerationJobStage stage;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String topic;

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

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "user_retry_count", nullable = false)
    private int userRetryCount;

    @Column(name = "automatic_retry_count", nullable = false)
    private int automaticRetryCount;

    protected DocumentGenerationJob() {}

    private DocumentGenerationJob(
            long batchId,
            long transcriptId,
            DocumentGenerationJobStage stage,
            String topic,
            Instant createdAt
    ) {
        validateBatchId(batchId);
        validateTranscriptId(transcriptId);
        validateCreatedAt(createdAt);
        this.batchId = batchId;
        this.stage = stage;
        this.topic = topic;
        this.transcriptId = transcriptId;
        this.status = DocumentGenerationJobStatus.QUEUED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.attemptCount = 1;
    }

    public static DocumentGenerationJob queueClassification(
            long batchId,
            long transcriptId,
            Instant createdAt
    ) {
        return new DocumentGenerationJob(
                batchId,
                transcriptId,
                DocumentGenerationJobStage.CLASSIFICATION,
                null,
                createdAt
        );
    }

    public static DocumentGenerationJob queueGeneration(
            long batchId,
            long transcriptId,
            DocumentTopic topic,
            Instant createdAt
    ) {
        validateTopic(topic);
        return new DocumentGenerationJob(
                batchId,
                transcriptId,
                DocumentGenerationJobStage.GENERATION,
                topic.value(),
                createdAt
        );
    }

    private static void validateTopic(DocumentTopic topic) {
        if (topic == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    public boolean isSucceeded() {
        return status == DocumentGenerationJobStatus.SUCCEEDED;
    }

    public boolean isFailed() {
        return status == DocumentGenerationJobStatus.FAILED;
    }

    public void startRunning(Instant startedAt) {
        if (status != DocumentGenerationJobStatus.QUEUED) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
        validateFailureTime(startedAt);
        status = DocumentGenerationJobStatus.RUNNING;
        updatedAt = startedAt;
    }

    public void validateRunningAttempt(int expectedAttemptCount) {
        validateAttempt(expectedAttemptCount);
        if (status != DocumentGenerationJobStatus.RUNNING) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
    }

    public void validateAttempt(int expectedAttemptCount) {
        if (attemptCount != expectedAttemptCount) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
    }

    public void validateClassificationStage() {
        if (stage != DocumentGenerationJobStage.CLASSIFICATION) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
    }

    public void validateGenerationStage() {
        if (stage != DocumentGenerationJobStage.GENERATION) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
    }

    public void recordSuccess(Instant completedAt) {
        validateRunningAttempt(attemptCount);
        validateFailureTime(completedAt);
        status = DocumentGenerationJobStatus.SUCCEEDED;
        updatedAt = completedAt;
    }

    private void validateBatchId(long batchId) {
        if (batchId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    public void retryByUser(Instant acceptedAt) {
        validateRetryState();
        validateUserRetryLimit();
        validateRetryTime(acceptedAt);
        validateRetryDeadline(acceptedAt);
        validateAttemptCapacity();
        status = DocumentGenerationJobStatus.QUEUED;
        updatedAt = acceptedAt;
        attemptCount++;
        userRetryCount++;
    }

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

    private void validateTranscriptId(long transcriptId) {
        if (transcriptId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    private void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
        validateFailureYear(createdAt);
    }

    private void validateRetryState() {
        if (status != DocumentGenerationJobStatus.FAILED) {
            throw new DocumentException(DocumentErrorCode.RETRY_NOT_ALLOWED);
        }
    }

    private void validateUserRetryLimit() {
        if (userRetryCount >= MAX_USER_RETRIES) {
            throw new DocumentException(DocumentErrorCode.RETRY_NOT_ALLOWED);
        }
    }

    private void validateRetryTime(Instant acceptedAt) {
        if (acceptedAt == null) {
            throw new DocumentException(DocumentErrorCode.RETRY_NOT_ALLOWED);
        }
        if (acceptedAt.isBefore(updatedAt)) {
            throw new DocumentException(DocumentErrorCode.RETRY_NOT_ALLOWED);
        }
        try {
            int year = acceptedAt.atOffset(ZoneOffset.UTC)
                    .getYear();
            if (year < 1 || year > 9999) {
                throw new DocumentException(DocumentErrorCode.RETRY_NOT_ALLOWED);
            }
        } catch (DateTimeException exception) {
            throw new DocumentException(DocumentErrorCode.RETRY_NOT_ALLOWED);
        }
    }

    private void validateRetryDeadline(Instant acceptedAt) {
        if (expiresAt == null || !acceptedAt.isBefore(expiresAt)) {
            throw new DocumentException(DocumentErrorCode.RETRY_NOT_ALLOWED);
        }
    }

    private void validateAttemptCapacity() {
        if (attemptCount == Integer.MAX_VALUE) {
            throw new DocumentException(DocumentErrorCode.RETRY_NOT_ALLOWED);
        }
    }
}

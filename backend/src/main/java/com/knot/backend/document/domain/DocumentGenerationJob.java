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
import lombok.Getter;

@Getter
@Entity
@Table(name = "document_generation_jobs")
public class DocumentGenerationJob {
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

    protected DocumentGenerationJob() {}

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

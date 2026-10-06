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
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private long workspaceId;

    @Column(name = "recording_session_id", nullable = false, updatable = false)
    private long recordingSessionId;

    @Column(name = "source_transcript_id", nullable = false, updatable = false)
    private long sourceTranscriptId;

    @Column(name = "document_generation_job_id", nullable = false, updatable = false)
    private long documentGenerationJobId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String topic;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String title;

    @Column(updatable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Document() {}

    private Document(
            long workspaceId,
            long recordingSessionId,
            long sourceTranscriptId,
            long documentGenerationJobId,
            String topic,
            String title,
            String summary,
            String content,
            Instant createdAt
    ) {
        validateSourceIdentifiers(
                workspaceId,
                recordingSessionId,
                sourceTranscriptId,
                documentGenerationJobId
        );
        validateRequiredText(topic);
        validateRequiredText(title);
        validateRequiredText(content);
        validateCreatedAt(createdAt);
        this.workspaceId = workspaceId;
        this.recordingSessionId = recordingSessionId;
        this.sourceTranscriptId = sourceTranscriptId;
        this.documentGenerationJobId = documentGenerationJobId;
        this.topic = topic;
        this.title = title;
        this.summary = summary;
        this.content = content;
        this.status = DocumentStatus.DRAFT;
        this.createdAt = createdAt;
    }

    public static Document createDraft(
            long workspaceId,
            long recordingSessionId,
            long sourceTranscriptId,
            long documentGenerationJobId,
            String topic,
            String title,
            String summary,
            String content,
            Instant createdAt
    ) {
        return new Document(
                workspaceId,
                recordingSessionId,
                sourceTranscriptId,
                documentGenerationJobId,
                topic,
                title,
                summary,
                content,
                createdAt
        );
    }

    private static void validateSourceIdentifiers(
            long workspaceId,
            long recordingSessionId,
            long sourceTranscriptId,
            long documentGenerationJobId
    ) {
        if (workspaceId <= 0 || recordingSessionId <= 0 || sourceTranscriptId <= 0 || documentGenerationJobId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    private static void validateRequiredText(String text) {
        if (text == null || text.isBlank()) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }
}

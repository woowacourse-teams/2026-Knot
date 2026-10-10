package com.knot.backend.document.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.Getter;

@Getter
@Entity
@Table(name = "document_generation_batches")
public class DocumentGenerationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_session_id", nullable = false, updatable = false)
    private long recordingSessionId;

    @Column(name = "transcript_id")
    private Long transcriptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_registration_state", nullable = false, length = 30)
    private DocumentTopicRegistrationState topicRegistrationState;

    @ElementCollection
    @CollectionTable(name = "document_generation_batch_topics", joinColumns = @JoinColumn(name = "batch_id"))
    @OrderColumn(name = "position")
    @Column(name = "topic", nullable = false, columnDefinition = "TEXT")
    private List<String> topics = new ArrayList<>();

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "cleanup_requested_at")
    private Instant cleanupRequestedAt;

    protected DocumentGenerationBatch() {}

    private DocumentGenerationBatch(
            long recordingSessionId,
            long transcriptId,
            Instant acceptedAt
    ) {
        validateIdentifier(recordingSessionId);
        validateIdentifier(transcriptId);
        validateTime(acceptedAt);
        this.recordingSessionId = recordingSessionId;
        this.transcriptId = transcriptId;
        this.acceptedAt = acceptedAt;
        this.topicRegistrationState = DocumentTopicRegistrationState.WAITING_CLASSIFICATION;
    }

    public static DocumentGenerationBatch accept(
            long recordingSessionId,
            long transcriptId,
            Instant acceptedAt
    ) {
        return new DocumentGenerationBatch(
                recordingSessionId,
                transcriptId,
                acceptedAt
        );
    }

    public List<String> getTopics() {
        return List.copyOf(topics);
    }

    public void validateInput(long inputTranscriptId) {
        if (transcriptId == null && topicRegistrationState != DocumentTopicRegistrationState.WAITING_CLASSIFICATION) {
            return;
        }
        if (transcriptId == null || transcriptId != inputTranscriptId) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
    }

    public boolean isRegisteredWith(List<DocumentTopic> classifiedTopics) {
        if (topicRegistrationState == DocumentTopicRegistrationState.WAITING_CLASSIFICATION) {
            return false;
        }
        List<String> names = classifiedTopics.stream()
                .map(DocumentTopic::value)
                .toList();
        return topics.equals(names);
    }

    public void validateRegisteredWith(List<DocumentTopic> classifiedTopics) {
        if (!isRegisteredWith(classifiedTopics)) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
    }

    public void registerTopics(
            List<DocumentTopic> classifiedTopics,
            Instant completedAt
    ) {
        validateWaiting();
        validateTime(completedAt);
        if (completedAt.isBefore(acceptedAt)) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
        validateTopics(classifiedTopics);
        topics.addAll(
                classifiedTopics.stream()
                        .map(DocumentTopic::value)
                        .toList()
        );
        registeredAt = completedAt;
        if (classifiedTopics.isEmpty()) {
            topicRegistrationState = DocumentTopicRegistrationState.NO_CONTENT;
            cleanupRequestedAt = completedAt;
            return;
        }
        topicRegistrationState = DocumentTopicRegistrationState.TOPICS_REGISTERED;
    }

    private void validateWaiting() {
        if (topicRegistrationState != DocumentTopicRegistrationState.WAITING_CLASSIFICATION) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
    }

    private void validateTopics(List<DocumentTopic> classifiedTopics) {
        if (classifiedTopics == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_RESPONSE);
        }
        for (DocumentTopic topic : classifiedTopics) {
            if (topic == null) {
                throw new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_RESPONSE);
            }
        }
        if (new HashSet<>(classifiedTopics).size() != classifiedTopics.size()) {
            throw new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_RESPONSE);
        }
    }

    private void validateIdentifier(long identifier) {
        if (identifier <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
    }

    private void validateTime(Instant time) {
        if (time == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
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
}

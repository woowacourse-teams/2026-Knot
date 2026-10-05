package com.knot.backend.recording.domain;

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
@Table(name = "recording_audio_uploads")
public class RecordingAudioUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_id", nullable = false, updatable = false)
    private Long recordingId;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "content_length", nullable = false)
    private long contentLength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordingAudioUploadStatus status;

    @Column(name = "reserved_at", nullable = false, updatable = false)
    private Instant reservedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected RecordingAudioUpload() {}

    private RecordingAudioUpload(
            Long recordingId,
            String storageKey,
            String contentType,
            long contentLength,
            Instant reservedAt
    ) {
        if (recordingId == null || recordingId <= 0 || storageKey == null || storageKey.isBlank()
                || reservedAt == null) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
        validateFile(
                contentType,
                contentLength
        );
        this.recordingId = recordingId;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.status = RecordingAudioUploadStatus.RESERVED;
        this.reservedAt = reservedAt;
    }

    public static RecordingAudioUpload reserve(
            Long recordingId,
            String storageKey,
            String contentType,
            long contentLength,
            Instant reservedAt
    ) {
        return new RecordingAudioUpload(
                recordingId,
                storageKey,
                contentType,
                contentLength,
                reservedAt
        );
    }

    public void changeFile(
            String contentType,
            long contentLength
    ) {
        if (status == RecordingAudioUploadStatus.COMPLETED) {
            throw new RecordingException(RecordingErrorCode.AUDIO_UPLOAD_ALREADY_COMPLETED);
        }
        validateFile(
                contentType,
                contentLength
        );
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    private void validateFile(
            String contentType,
            long contentLength
    ) {
        if (contentType == null || contentType.isBlank() || contentLength <= 0) {
            throw new RecordingException(RecordingErrorCode.INVALID_AUDIO_UPLOAD);
        }
    }
}

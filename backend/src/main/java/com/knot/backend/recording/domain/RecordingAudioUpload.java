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
    private static final String ALLOWED_CONTENT_TYPE = "audio/webm";
    private static final long MAX_CONTENT_LENGTH = 500L * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_id", nullable = false, updatable = false)
    private Long recordingId;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "content_length", nullable = false, updatable = false)
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

    // 이전에 발급한 URL도 만료 전까지 같은 key에 PUT할 수 있으므로, 재발급 때 파일 형식·크기를 바꾸지 않는다.
    public void validateReissuable(
            String contentType,
            long contentLength
    ) {
        if (status == RecordingAudioUploadStatus.COMPLETED) {
            throw new RecordingException(RecordingErrorCode.AUDIO_UPLOAD_ALREADY_COMPLETED);
        }
        if (!this.contentType.equals(contentType) || this.contentLength != contentLength) {
            throw new RecordingException(RecordingErrorCode.INVALID_AUDIO_UPLOAD);
        }
    }

    public void validateBelongsTo(long recordingId) {
        if (this.recordingId != recordingId) {
            throw new RecordingException(RecordingErrorCode.AUDIO_UPLOAD_NOT_FOUND);
        }
    }

    public boolean isCompleted() {
        return status == RecordingAudioUploadStatus.COMPLETED;
    }

    public void complete(
            long storedContentLength,
            String storedContentType,
            Instant completedAt
    ) {
        if (isCompleted()) {
            return;
        }
        if (storedContentLength != contentLength || !contentType.equals(storedContentType)) {
            throw new RecordingException(RecordingErrorCode.AUDIO_UPLOAD_NOT_COMPLETED);
        }
        if (completedAt == null || completedAt.isBefore(reservedAt)) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_TIME);
        }
        this.status = RecordingAudioUploadStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    private void validateFile(
            String contentType,
            long contentLength
    ) {
        if (!ALLOWED_CONTENT_TYPE.equals(contentType) || contentLength <= 0 || contentLength > MAX_CONTENT_LENGTH) {
            throw new RecordingException(RecordingErrorCode.INVALID_AUDIO_UPLOAD);
        }
    }
}

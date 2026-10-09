package com.knot.backend.recording.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "transcript_segments")
public class TranscriptSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transcript_id", nullable = false, updatable = false)
    private long transcriptId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(name = "start_millis", nullable = false, updatable = false)
    private long startMillis;

    @Column(name = "end_millis", updatable = false)
    private Long endMillis;

    @Column(name = "speaker_number", updatable = false)
    private Integer speakerNumber;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String text;

    protected TranscriptSegment() {}

    private TranscriptSegment(
            long transcriptId,
            int position,
            long startMillis,
            Long endMillis,
            Integer speakerNumber,
            String text
    ) {
        validateTranscriptId(transcriptId);
        validatePosition(position);
        validateTimeRange(
                startMillis,
                endMillis
        );
        validateSpeakerNumber(speakerNumber);
        validateText(text);
        this.transcriptId = transcriptId;
        this.position = position;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.speakerNumber = speakerNumber;
        this.text = text;
    }

    public static TranscriptSegment create(
            long transcriptId,
            int position,
            long startMillis,
            Long endMillis,
            Integer speakerNumber,
            String text
    ) {
        return new TranscriptSegment(
                transcriptId,
                position,
                startMillis,
                endMillis,
                speakerNumber,
                text
        );
    }

    private void validateTranscriptId(long transcriptId) {
        if (transcriptId <= 0) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
    }

    private void validatePosition(int position) {
        if (position < 0) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
    }

    private void validateTimeRange(
            long startMillis,
            Long endMillis
    ) {
        if (startMillis < 0) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
        if (endMillis != null && endMillis < startMillis) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
    }

    private void validateSpeakerNumber(Integer speakerNumber) {
        if (speakerNumber != null && speakerNumber < 1) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
    }

    private void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
    }
}

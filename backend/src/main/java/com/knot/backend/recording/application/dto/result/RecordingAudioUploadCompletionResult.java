package com.knot.backend.recording.application.dto.result;

import com.knot.backend.recording.domain.RecordingAudioUpload;
import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import java.time.Instant;

public record RecordingAudioUploadCompletionResult(
        long recordingId,
        long uploadId,
        RecordingAudioUploadStatus uploadStatus,
        Instant completedAt
) {

    public static RecordingAudioUploadCompletionResult from(RecordingAudioUpload upload) {
        return new RecordingAudioUploadCompletionResult(
                upload.getRecordingId(),
                upload.getId(),
                upload.getStatus(),
                upload.getCompletedAt()
        );
    }
}

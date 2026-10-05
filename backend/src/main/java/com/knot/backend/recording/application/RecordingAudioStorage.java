package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.result.PresignedAudioUpload;

public interface RecordingAudioStorage {

    PresignedAudioUpload presignUpload(
            String storageKey,
            String contentType,
            long contentLength
    );
}

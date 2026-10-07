package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.result.PresignedAudioUpload;
import com.knot.backend.recording.application.dto.result.StoredAudioObject;

public interface RecordingAudioStorage {

    PresignedAudioUpload presignUpload(
            String storageKey,
            String contentType,
            long contentLength
    );

    StoredAudioObject findStoredObject(String storageKey);
}

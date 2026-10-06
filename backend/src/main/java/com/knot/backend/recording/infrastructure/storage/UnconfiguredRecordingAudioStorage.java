package com.knot.backend.recording.infrastructure.storage;

import com.knot.backend.recording.application.RecordingAudioStorage;
import com.knot.backend.recording.application.dto.result.PresignedAudioUpload;
import com.knot.backend.recording.application.dto.result.StoredAudioObject;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;

// 저장소 설정 전에도 서버가 뜨도록 하고, 업로드 URL 요청만 명시적으로 거절한다.
public class UnconfiguredRecordingAudioStorage implements RecordingAudioStorage {

    @Override
    public PresignedAudioUpload presignUpload(
            String storageKey,
            String contentType,
            long contentLength
    ) {
        throw new RecordingException(RecordingErrorCode.AUDIO_STORAGE_UNAVAILABLE);
    }

    @Override
    public StoredAudioObject findStoredObject(String storageKey) {
        throw new RecordingException(RecordingErrorCode.AUDIO_STORAGE_UNAVAILABLE);
    }
}

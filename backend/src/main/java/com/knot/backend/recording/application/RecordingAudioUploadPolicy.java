package com.knot.backend.recording.application;

import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import java.util.Set;

public record RecordingAudioUploadPolicy(
        Set<String> allowedContentTypes,
        long maxContentLength
) {

    public void validate(
            String contentType,
            long contentLength
    ) {
        if (!allowedContentTypes.contains(contentType) || contentLength <= 0 || contentLength > maxContentLength) {
            throw new RecordingException(RecordingErrorCode.INVALID_AUDIO_UPLOAD);
        }
    }
}

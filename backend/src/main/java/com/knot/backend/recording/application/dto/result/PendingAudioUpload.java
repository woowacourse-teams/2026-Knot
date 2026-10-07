package com.knot.backend.recording.application.dto.result;

public record PendingAudioUpload(
        String storageKey,
        boolean completed
) {
}

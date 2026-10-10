package com.knot.backend.recording.application.dto.result;

public record RecordingAudioDeletionExecution(
        long taskId,
        int attemptCount,
        String storageKey
) {

}

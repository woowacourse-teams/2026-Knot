package com.knot.backend.recording.application.dto.command;

public record RecordingAudioUploadUrlCommand(
        String contentType,
        long contentLength
) {
}

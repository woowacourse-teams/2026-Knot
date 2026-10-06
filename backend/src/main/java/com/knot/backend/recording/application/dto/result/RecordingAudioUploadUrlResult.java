package com.knot.backend.recording.application.dto.result;

import java.time.Instant;

public record RecordingAudioUploadUrlResult(
        long uploadId,
        String uploadUrl,
        Instant expiresAt,
        boolean created
) {
}

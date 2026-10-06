package com.knot.backend.recording.application.dto.result;

import java.time.Instant;

public record PresignedAudioUpload(
        String uploadUrl,
        Instant expiresAt
) {
}

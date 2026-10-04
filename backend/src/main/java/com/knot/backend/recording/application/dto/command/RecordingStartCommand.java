package com.knot.backend.recording.application.dto.command;

import java.util.UUID;

public record RecordingStartCommand(
        UUID requestId,
        UUID tabId,
        String controlToken
) {

    @Override
    public String toString() {
        return "RecordingStartCommand[controlToken=REDACTED]";
    }
}

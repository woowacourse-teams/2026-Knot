package com.knot.backend.recording.application.dto.command;

import java.util.UUID;

public record RecordingControlCommand(
        UUID tabId,
        String controlToken
) {

    @Override
    public String toString() {
        return "RecordingControlCommand[controlToken=REDACTED]";
    }
}

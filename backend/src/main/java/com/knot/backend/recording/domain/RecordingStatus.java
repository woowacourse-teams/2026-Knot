package com.knot.backend.recording.domain;

import java.util.Set;

public enum RecordingStatus {
    RECORDING,
    PAUSED,
    ENDED;

    public static final Set<RecordingStatus> ACTIVE_STATUSES = Set.of(
            RECORDING,
            PAUSED
    );
}

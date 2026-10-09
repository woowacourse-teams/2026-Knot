package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.result.RecordingDetailSnapshot;
import java.util.Optional;

public interface RecordingDetailQuery {

    Optional<RecordingDetailSnapshot> find(
            long workspaceId,
            long recordingId
    );
}

package com.knot.backend.recording.domain;

import java.util.Optional;
import java.util.UUID;

public interface RecordingSessionRepository {

    RecordingSession save(RecordingSession recordingSession);

    Optional<RecordingSession> findById(Long recordingSessionId);

    Optional<RecordingSession> findByMemberIdAndRequestId(
            long memberId,
            UUID requestId
    );

    boolean existsActiveByMemberId(long memberId);
}

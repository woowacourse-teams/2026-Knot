package com.knot.backend.recording.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingSessionRepository {

    RecordingSession save(RecordingSession recordingSession);

    Optional<RecordingSession> findById(Long recordingSessionId);

    Optional<RecordingSession> findByIdForUpdate(long recordingSessionId);

    Optional<RecordingSession> findByMemberIdAndRequestId(
            long memberId,
            UUID requestId
    );

    boolean existsActiveByMemberId(long memberId);

    Optional<RecordingSession> findActiveByWorkspaceIdAndMemberId(
            long workspaceId,
            long memberId
    );

    List<RecordingSession> findAllActiveByWorkspaceIdForUpdate(long workspaceId);

    List<RecordingSession> findAllActiveByWorkspaceIdAndMemberIdForUpdate(
            long workspaceId,
            long memberId
    );
}

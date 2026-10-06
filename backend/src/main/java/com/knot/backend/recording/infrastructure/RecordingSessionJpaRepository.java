package com.knot.backend.recording.infrastructure;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface RecordingSessionJpaRepository extends JpaRepository<RecordingSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecordingSession> findWithLockById(long id);

    Optional<RecordingSession> findByMemberIdAndRequestId(
            long memberId,
            UUID requestId
    );

    boolean existsByMemberIdAndStatusIn(
            long memberId,
            Collection<RecordingStatus> statuses
    );

    Optional<RecordingSession> findByWorkspaceIdAndMemberIdAndStatusIn(
            long workspaceId,
            long memberId,
            Collection<RecordingStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RecordingSession> findAllByWorkspaceIdAndStatusInOrderByIdAsc(
            long workspaceId,
            Collection<RecordingStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RecordingSession> findAllByWorkspaceIdAndMemberIdAndStatusInOrderByIdAsc(
            long workspaceId,
            long memberId,
            Collection<RecordingStatus> statuses
    );
}

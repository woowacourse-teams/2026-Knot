package com.knot.backend.recording.infrastructure;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RecordingSessionJpaRepository extends JpaRepository<RecordingSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecordingSession> findWithLockById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecordingSession> findWithLockByMemberIdAndRequestId(
            long memberId,
            UUID requestId
    );

    @Query(value = """
            SELECT id FROM recording_sessions
            WHERE status IN ('RECORDING', 'PAUSED') AND last_seen_at <= :threshold
            ORDER BY last_seen_at, id
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findActiveIdsLastSeenAtOrBefore(
            Instant threshold,
            int limit
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RecordingSession> findAllByMemberIdAndStatusInOrderByIdAsc(
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

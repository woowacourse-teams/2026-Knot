package com.knot.backend.recording.infrastructure;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecordingSessionJpaRepository extends JpaRepository<RecordingSession, Long> {

    Optional<RecordingSession> findByMemberIdAndRequestId(
            long memberId,
            UUID requestId
    );

    boolean existsByMemberIdAndStatusIn(
            long memberId,
            Collection<RecordingStatus> statuses
    );
}

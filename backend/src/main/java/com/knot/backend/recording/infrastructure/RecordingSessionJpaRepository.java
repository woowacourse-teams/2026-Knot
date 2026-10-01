package com.knot.backend.recording.infrastructure;

import com.knot.backend.recording.domain.RecordingSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecordingSessionJpaRepository extends JpaRepository<RecordingSession, Long> {

    Optional<RecordingSession> findByMemberIdAndRequestId(
            long memberId,
            UUID requestId
    );

    @Query("""
            SELECT COUNT(recordingSession) > 0
            FROM RecordingSession recordingSession
            WHERE recordingSession.memberId = :memberId
                AND recordingSession.status IN (
                    com.knot.backend.recording.domain.RecordingStatus.RECORDING,
                    com.knot.backend.recording.domain.RecordingStatus.PAUSED
                )
            """)
    boolean existsActiveByMemberId(@Param("memberId") long memberId);
}

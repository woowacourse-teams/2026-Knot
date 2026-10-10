package com.knot.backend.recording.infrastructure;

import com.knot.backend.recording.domain.RecordingSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface RecordingDetailReadJpaRepository extends Repository<RecordingSession, Long> {

    // 세션과 업로드 예약을 한 문장으로 읽어 종료·업로드 완료와 겹쳐도 서로 다른 시점이 섞이지 않게 한다
    @Query("""
            select new com.knot.backend.recording.infrastructure.RecordingDetailRow(
                rs.id, rs.memberId, rs.status, rs.startedAt, rs.currentIntervalStartedAt,
                rs.accumulatedRecordingMillis, rs.lastSeenAt, rs.endedAt, rs.endReason,
                u.id, u.status, u.completedAt
            )
            from RecordingSession rs
            left join RecordingAudioUpload u on u.recordingId = rs.id
            where rs.workspaceId = :workspaceId and rs.id = :recordingId
            """)
    Optional<RecordingDetailRow> findDetail(
            long workspaceId,
            long recordingId
    );
}

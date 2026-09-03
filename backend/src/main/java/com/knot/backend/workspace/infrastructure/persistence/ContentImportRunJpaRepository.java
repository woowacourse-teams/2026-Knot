package com.knot.backend.workspace.infrastructure.persistence;

import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ContentImportRunJpaRepository extends JpaRepository<ContentImportRun, Long> {

    @Query(value = """
            SELECT *
            FROM content_import_runs
            WHERE status = 'PENDING'
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ContentImportRun> findFirstPendingForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT importRun
            FROM ContentImportRun importRun
            WHERE importRun.id = :importRunId
            """)
    Optional<ContentImportRun> findByIdForUpdate(Long importRunId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE content_import_runs
            SET last_heartbeat_at = CURRENT_TIMESTAMP
            WHERE id = :importRunId
                AND status = 'RUNNING'
            """, nativeQuery = true)
    int heartbeatIfRunning(Long importRunId);

    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    Instant currentDatabaseTime();

    @Query(value = """
            SELECT *
            FROM content_import_runs
            WHERE status = 'RUNNING'
                AND last_heartbeat_at <= CURRENT_TIMESTAMP
                    - CAST(:runningTimeoutMillis AS DOUBLE PRECISION) * INTERVAL '1 millisecond'
                AND started_at <= CURRENT_TIMESTAMP
                    - CAST(:runningTimeoutMillis AS DOUBLE PRECISION) * INTERVAL '1 millisecond'
            ORDER BY last_heartbeat_at ASC, id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ContentImportRun> findStaleRunningForUpdate(
            long runningTimeoutMillis,
            int batchSize
    );

    Optional<ContentImportRun> findFirstByContentSourceConnectionIdAndStatusIn(
            Long contentSourceConnectionId,
            Collection<ContentImportStatus> statuses
    );

    @Query("""
            SELECT importRun
            FROM ContentImportRun importRun
            WHERE importRun.id = :importRunId
                AND EXISTS (
                    SELECT workspaceMember.id
                    FROM WorkspaceMember workspaceMember
                    WHERE workspaceMember.workspaceId = importRun.workspaceId
                        AND workspaceMember.memberId = :memberId
                )
            """)
    Optional<ContentImportRun> findVisibleByIdAndMemberId(
            Long importRunId,
            long memberId
    );
}
